package com.dentalcore.reminders.internal.repository;

import com.dentalcore.reminders.internal.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    boolean existsByAppointmentIdAndType(UUID appointmentId, Reminder.Type type);

    boolean existsByPatientIdAndTypeAndSentAtAfter(
            UUID patientId, Reminder.Type type, Instant after);
}
