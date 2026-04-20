package com.dentalcore.infrastructure.notifications;

import com.dentalcore.shared.notifications.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev-only: surfaces outbound messages in logs instead of sending them. */
@Component
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void sendPasswordResetLink(String email, String resetUrl) {
        log.info("Password reset requested for {}. Reset link: {}", email, resetUrl);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("[EMAIL → {}] {} :: {}", to, subject, body);
    }

    @Override
    public void sendSms(String to, String body) {
        log.info("[SMS → {}] {}", to, body);
    }
}
