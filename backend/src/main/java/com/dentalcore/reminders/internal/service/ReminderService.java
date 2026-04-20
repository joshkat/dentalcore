package com.dentalcore.reminders.internal.service;

import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.reminders.internal.entity.Reminder;
import com.dentalcore.reminders.internal.repository.ReminderRepository;
import com.dentalcore.shared.notifications.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appointment and recall reminders. Reads contact/consent data with SQL
 * (read-model, like reporting), writes its own reminder log, and sends
 * through the NotificationPort. Consent is enforced here: no consented
 * channel means a SKIPPED log entry, never a message.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Duration RECALL_RESEND_COOLDOWN = Duration.ofDays(30);

    private final NamedParameterJdbcTemplate jdbc;
    private final ReminderRepository reminderRepository;
    private final NotificationPort notifications;
    private final ClinicTimeService clinicTime;

    public ReminderService(NamedParameterJdbcTemplate jdbc,
                           ReminderRepository reminderRepository,
                           NotificationPort notifications,
                           ClinicTimeService clinicTime) {
        this.jdbc = jdbc;
        this.reminderRepository = reminderRepository;
        this.notifications = notifications;
        this.clinicTime = clinicTime;
    }

    public record RunSummary(int appointmentSent, int appointmentSkipped,
                             int recallSent, int recallSkipped, int failed) {
    }

    public record RecallWorklistRow(UUID patientId, String firstName, String lastName,
                                    LocalDate nextRecallDate, String phone, String email,
                                    Instant lastReminderAt) {
    }

    private record Contact(UUID patientId, String firstName, String email, boolean emailConsent,
                           String phone, boolean smsConsent, String preferredMethod) {
    }

    @Scheduled(cron = "${dentalcore.reminders.cron:0 0 9 * * *}")
    public void scheduledRun() {
        RunSummary summary = runAll();
        log.info("Reminder run: {}", summary);
    }

    @Transactional
    public RunSummary runAll() {
        int[] appointment = runAppointmentReminders();
        int[] recall = runRecallReminders();
        return new RunSummary(appointment[0], appointment[1], recall[0], recall[1],
                appointment[2] + recall[2]);
    }

    private int[] runAppointmentReminders() {
        ZoneId zone = clinicTime.clinicZone(DEFAULT_CLINIC_ID);
        ZonedDateTime tomorrowStart = clinicTime.startOfToday(DEFAULT_CLINIC_ID).plusDays(1);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id AS appointment_id, a.starts_at, p.id AS patient_id, p.first_name,
                       p.email, p.email_consent, p.sms_consent, p.preferred_contact_method,
                       (SELECT ph.number FROM patient_phones ph
                        WHERE ph.patient_id = p.id ORDER BY ph.is_primary DESC LIMIT 1) AS phone
                FROM appointments a
                JOIN patients p ON p.id = a.patient_id AND p.deleted_at IS NULL
                WHERE a.deleted_at IS NULL
                  AND a.status IN ('SCHEDULED', 'CONFIRMED')
                  AND a.starts_at >= :from AND a.starts_at < :to
                """, Map.of(
                "from", tomorrowStart.toOffsetDateTime(),
                "to", tomorrowStart.plusDays(1).toOffsetDateTime()));

        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (Map<String, Object> row : rows) {
            UUID appointmentId = (UUID) row.get("appointment_id");
            if (reminderRepository.existsByAppointmentIdAndType(
                    appointmentId, Reminder.Type.APPOINTMENT)) {
                continue;
            }
            Contact contact = contactFrom(row);
            ZonedDateTime when = toInstant(row.get("starts_at")).atZone(zone);
            String message = "Hi %s, this is a reminder of your dental appointment on %s at %s."
                    .formatted(contact.firstName(),
                            when.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                            when.format(DateTimeFormatter.ofPattern("h:mm a")));
            int outcome = deliver(contact, appointmentId, Reminder.Type.APPOINTMENT,
                    "Appointment reminder", message);
            if (outcome == 0) sent++;
            else if (outcome == 1) skipped++;
            else failed++;
        }
        return new int[]{sent, skipped, failed};
    }

    private int[] runRecallReminders() {
        LocalDate horizon = clinicTime.today(DEFAULT_CLINIC_ID).plusDays(7);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id AS patient_id, p.first_name, p.email, p.email_consent,
                       p.sms_consent, p.preferred_contact_method, p.next_recall_date,
                       (SELECT ph.number FROM patient_phones ph
                        WHERE ph.patient_id = p.id ORDER BY ph.is_primary DESC LIMIT 1) AS phone
                FROM patients p
                WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE'
                  AND p.next_recall_date IS NOT NULL AND p.next_recall_date <= :horizon
                """, Map.of("horizon", horizon));

        int sent = 0;
        int skipped = 0;
        int failed = 0;
        Instant cooldownCutoff = Instant.now().minus(RECALL_RESEND_COOLDOWN);
        for (Map<String, Object> row : rows) {
            Contact contact = contactFrom(row);
            if (reminderRepository.existsByPatientIdAndTypeAndSentAtAfter(
                    contact.patientId(), Reminder.Type.RECALL, cooldownCutoff)) {
                continue;
            }
            String message = ("Hi %s, you're due for your dental recall visit. "
                    + "Please call us to schedule your appointment.")
                    .formatted(contact.firstName());
            int outcome = deliver(contact, null, Reminder.Type.RECALL,
                    "Time for your dental check-up", message);
            if (outcome == 0) sent++;
            else if (outcome == 1) skipped++;
            else failed++;
        }
        return new int[]{sent, skipped, failed};
    }

    /** @return 0 sent, 1 skipped, 2 failed */
    private int deliver(Contact contact, UUID appointmentId, Reminder.Type type,
                        String subject, String message) {
        boolean smsPossible = contact.smsConsent() && contact.phone() != null;
        boolean emailPossible = contact.emailConsent() && contact.email() != null;
        boolean preferSms = "SMS".equals(contact.preferredMethod())
                || (contact.preferredMethod() == null && smsPossible);

        Reminder.Channel channel;
        if (preferSms && smsPossible) {
            channel = Reminder.Channel.SMS;
        } else if (emailPossible) {
            channel = Reminder.Channel.EMAIL;
        } else if (smsPossible) {
            channel = Reminder.Channel.SMS;
        } else {
            reminderRepository.save(new Reminder(contact.patientId(), appointmentId, type,
                    Reminder.Channel.NONE, Reminder.Status.SKIPPED,
                    "No consented contact channel"));
            return 1;
        }

        try {
            if (channel == Reminder.Channel.SMS) {
                notifications.sendSms(contact.phone(), message);
            } else {
                notifications.sendEmail(contact.email(), subject, message);
            }
            reminderRepository.save(new Reminder(contact.patientId(), appointmentId, type,
                    channel, Reminder.Status.SENT,
                    channel == Reminder.Channel.SMS ? contact.phone() : contact.email()));
            return 0;
        } catch (Exception e) {
            log.warn("Reminder delivery failed for patient {}", contact.patientId(), e);
            reminderRepository.save(new Reminder(contact.patientId(), appointmentId, type,
                    channel, Reminder.Status.FAILED, e.getMessage()));
            return 2;
        }
    }

    @Transactional(readOnly = true)
    public List<RecallWorklistRow> recallWorklist(int daysAhead) {
        LocalDate horizon = clinicTime.today(DEFAULT_CLINIC_ID)
                .plusDays(Math.min(Math.max(daysAhead, 0), 365));
        return jdbc.query("""
                SELECT p.id, p.first_name, p.last_name, p.next_recall_date, p.email,
                       (SELECT ph.number FROM patient_phones ph
                        WHERE ph.patient_id = p.id ORDER BY ph.is_primary DESC LIMIT 1) AS phone,
                       (SELECT MAX(r.sent_at) FROM reminders r
                        WHERE r.patient_id = p.id AND r.type = 'RECALL'
                          AND r.status = 'SENT') AS last_reminder_at
                FROM patients p
                WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE'
                  AND p.next_recall_date IS NOT NULL AND p.next_recall_date <= :horizon
                ORDER BY p.next_recall_date
                """, Map.of("horizon", horizon),
                (rs, i) -> new RecallWorklistRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getObject("next_recall_date", LocalDate.class),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getTimestamp("last_reminder_at") == null
                                ? null : rs.getTimestamp("last_reminder_at").toInstant()));
    }

    /** JDBC may hand back Timestamp or OffsetDateTime depending on driver path. */
    private Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }

    private Contact contactFrom(Map<String, Object> row) {
        return new Contact(
                (UUID) row.get("patient_id"),
                (String) row.get("first_name"),
                (String) row.get("email"),
                Boolean.TRUE.equals(row.get("email_consent")),
                (String) row.get("phone"),
                Boolean.TRUE.equals(row.get("sms_consent")),
                (String) row.get("preferred_contact_method"));
    }
}
