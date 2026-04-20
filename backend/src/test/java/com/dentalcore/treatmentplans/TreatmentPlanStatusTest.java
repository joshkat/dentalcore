package com.dentalcore.treatmentplans;

import com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus;
import org.junit.jupiter.api.Test;

import static com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus.APPROVED;
import static com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus.CANCELLED;
import static com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus.COMPLETED;
import static com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus.DRAFT;
import static com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus.IN_PROGRESS;
import static com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus.PRESENTED;
import static org.assertj.core.api.Assertions.assertThat;

class TreatmentPlanStatusTest {

    @Test
    void happyPathFlowIsAllowed() {
        assertThat(DRAFT.canTransitionTo(PRESENTED)).isTrue();
        assertThat(PRESENTED.canTransitionTo(APPROVED)).isTrue();
        assertThat(APPROVED.canTransitionTo(IN_PROGRESS)).isTrue();
        assertThat(IN_PROGRESS.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void presentedPlanCanReturnToDraftForRework() {
        assertThat(PRESENTED.canTransitionTo(DRAFT)).isTrue();
    }

    @Test
    void skippingApprovalIsRejected() {
        assertThat(DRAFT.canTransitionTo(APPROVED)).isFalse();
        assertThat(DRAFT.canTransitionTo(IN_PROGRESS)).isFalse();
        assertThat(PRESENTED.canTransitionTo(IN_PROGRESS)).isFalse();
        assertThat(APPROVED.canTransitionTo(COMPLETED)).isFalse();
    }

    @Test
    void terminalStatesAllowNothing() {
        for (TreatmentPlanStatus terminal : new TreatmentPlanStatus[]{COMPLETED, CANCELLED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (TreatmentPlanStatus target : TreatmentPlanStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void procedureEditsOnlyWhileShapingThePlan() {
        assertThat(DRAFT.allowsProcedureEdits()).isTrue();
        assertThat(PRESENTED.allowsProcedureEdits()).isTrue();
        assertThat(APPROVED.allowsProcedureEdits()).isFalse();
        assertThat(IN_PROGRESS.allowsProcedureEdits()).isFalse();
        assertThat(COMPLETED.allowsProcedureEdits()).isFalse();
    }
}
