package com.dentalcore.shared.notifications;

/**
 * Outbound notification abstraction. Local/dev uses a logging adapter;
 * production swaps in SMTP/SES/Twilio adapters without touching callers.
 */
public interface NotificationPort {

    void sendPasswordResetLink(String email, String resetUrl);

    void sendEmail(String to, String subject, String body);

    void sendSms(String to, String body);
}
