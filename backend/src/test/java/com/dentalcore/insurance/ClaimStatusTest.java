package com.dentalcore.insurance;

import com.dentalcore.insurance.internal.entity.ClaimStatus;
import org.junit.jupiter.api.Test;

import static com.dentalcore.insurance.internal.entity.ClaimStatus.ACCEPTED;
import static com.dentalcore.insurance.internal.entity.ClaimStatus.CLOSED;
import static com.dentalcore.insurance.internal.entity.ClaimStatus.DENIED;
import static com.dentalcore.insurance.internal.entity.ClaimStatus.DRAFT;
import static com.dentalcore.insurance.internal.entity.ClaimStatus.PAID;
import static com.dentalcore.insurance.internal.entity.ClaimStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimStatusTest {

    @Test
    void happyPathFlowIsAllowed() {
        assertThat(DRAFT.canTransitionTo(SUBMITTED)).isTrue();
        assertThat(SUBMITTED.canTransitionTo(ACCEPTED)).isTrue();
        assertThat(ACCEPTED.canTransitionTo(PAID)).isTrue();
        assertThat(PAID.canTransitionTo(CLOSED)).isTrue();
    }

    @Test
    void deniedClaimsCanBeResubmittedOrWrittenOff() {
        assertThat(SUBMITTED.canTransitionTo(DENIED)).isTrue();
        assertThat(DENIED.canTransitionTo(SUBMITTED)).isTrue();
        assertThat(DENIED.canTransitionTo(CLOSED)).isTrue();
    }

    @Test
    void skippingStepsIsRejected() {
        assertThat(DRAFT.canTransitionTo(ACCEPTED)).isFalse();
        assertThat(DRAFT.canTransitionTo(PAID)).isFalse();
        assertThat(SUBMITTED.canTransitionTo(PAID)).isFalse();
        assertThat(SUBMITTED.canTransitionTo(CLOSED)).isFalse();
    }

    @Test
    void closedIsTerminal() {
        assertThat(CLOSED.isTerminal()).isTrue();
        for (ClaimStatus target : ClaimStatus.values()) {
            assertThat(CLOSED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void editAndPaymentWindowsAreCorrect() {
        assertThat(DRAFT.allowsLineItemEdits()).isTrue();
        assertThat(SUBMITTED.allowsLineItemEdits()).isFalse();
        assertThat(ACCEPTED.allowsPayments()).isTrue();
        assertThat(SUBMITTED.allowsPayments()).isFalse();
        assertThat(PAID.allowsPayments()).isFalse();
    }
}
