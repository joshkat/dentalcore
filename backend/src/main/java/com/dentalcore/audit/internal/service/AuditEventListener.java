package com.dentalcore.audit.internal.service;

import com.dentalcore.audit.internal.entity.AuditLog;
import com.dentalcore.audit.internal.repository.AuditLogRepository;
import com.dentalcore.shared.events.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditEventListener {

    private final AuditLogRepository repository;

    public AuditEventListener(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * REQUIRES_NEW so audit records survive a rollback of the business transaction —
     * a failed login must still be recorded even though the request itself fails.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AuditEvent event) {
        repository.save(new AuditLog(
                event.userId(),
                null,
                event.entityType(),
                event.entityId(),
                event.action().name(),
                event.previousValue(),
                event.newValue(),
                currentIpAddress()));
    }

    private String currentIpAddress() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
