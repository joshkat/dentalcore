package com.dentalcore.appointments;

import com.dentalcore.appointments.internal.entity.AppointmentStatus;
import org.junit.jupiter.api.Test;

import static com.dentalcore.appointments.internal.entity.AppointmentStatus.CANCELLED;
import static com.dentalcore.appointments.internal.entity.AppointmentStatus.CHECKED_IN;
import static com.dentalcore.appointments.internal.entity.AppointmentStatus.COMPLETED;
import static com.dentalcore.appointments.internal.entity.AppointmentStatus.CONFIRMED;
import static com.dentalcore.appointments.internal.entity.AppointmentStatus.IN_PROGRESS;
import static com.dentalcore.appointments.internal.entity.AppointmentStatus.NO_SHOW;
import static com.dentalcore.appointments.internal.entity.AppointmentStatus.SCHEDULED;
import static org.assertj.core.api.Assertions.assertThat;

class AppointmentStatusTest {

    @Test
    void happyPathFlowIsAllowed() {
        assertThat(SCHEDULED.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(CHECKED_IN)).isTrue();
        assertThat(CHECKED_IN.canTransitionTo(IN_PROGRESS)).isTrue();
        assertThat(IN_PROGRESS.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void walkInsCanSkipConfirmation() {
        assertThat(SCHEDULED.canTransitionTo(CHECKED_IN)).isTrue();
    }

    @Test
    void terminalStatesAllowNothing() {
        for (AppointmentStatus terminal : new AppointmentStatus[]{COMPLETED, CANCELLED, NO_SHOW}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (AppointmentStatus target : AppointmentStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void backwardsAndSkippingTransitionsAreRejected() {
        assertThat(CONFIRMED.canTransitionTo(SCHEDULED)).isFalse();
        assertThat(SCHEDULED.canTransitionTo(COMPLETED)).isFalse();
        assertThat(SCHEDULED.canTransitionTo(IN_PROGRESS)).isFalse();
        assertThat(IN_PROGRESS.canTransitionTo(CANCELLED)).isFalse();
        assertThat(IN_PROGRESS.canTransitionTo(NO_SHOW)).isFalse();
    }

    @Test
    void cancelledAndNoShowDoNotBlockTheCalendar() {
        assertThat(CANCELLED.blocksCalendar()).isFalse();
        assertThat(NO_SHOW.blocksCalendar()).isFalse();
        assertThat(SCHEDULED.blocksCalendar()).isTrue();
        assertThat(COMPLETED.blocksCalendar()).isTrue();
    }
}
