package com.dentalcore.appointments.api;

import java.util.Optional;
import java.util.UUID;

/** Public lookup interface of the appointments module. */
public interface AppointmentApi {

    Optional<AppointmentView> findView(UUID appointmentId);
}
