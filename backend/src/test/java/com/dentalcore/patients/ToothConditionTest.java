package com.dentalcore.patients;

import com.dentalcore.patients.internal.entity.ToothCondition;
import com.dentalcore.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToothConditionTest {

    @Test
    void permanentAndPrimaryTeethAreAccepted() {
        assertThat(ToothCondition.normalizeTooth("11")).isEqualTo("11");
        assertThat(ToothCondition.normalizeTooth("48")).isEqualTo("48");
        assertThat(ToothCondition.normalizeTooth("51")).isEqualTo("51");
        assertThat(ToothCondition.normalizeTooth(" 85 ")).isEqualTo("85");
    }

    @Test
    void invalidTeethAreRejected() {
        // Universal notation (1-32, A-T) and out-of-range FDI digits are rejected
        for (String bad : new String[]{"0", "1", "8", "10", "19", "49", "56", "86", "90",
                "A", "T", "1A", "", "  "}) {
            assertThatThrownBy(() -> ToothCondition.normalizeTooth(bad))
                    .as("tooth '%s'", bad)
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    void surfacesAreNormalizedDedupedAndOrdered() {
        assertThat(ToothCondition.normalizeSurfaces("mod")).isEqualTo("MOD");
        assertThat(ToothCondition.normalizeSurfaces("DOM")).isEqualTo("MOD");
        assertThat(ToothCondition.normalizeSurfaces("OOM")).isEqualTo("MO");
        assertThat(ToothCondition.normalizeSurfaces("")).isNull();
        assertThat(ToothCondition.normalizeSurfaces(null)).isNull();
    }

    @Test
    void invalidSurfaceLettersAreRejected() {
        assertThatThrownBy(() -> ToothCondition.normalizeSurfaces("MX"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void resolvedConditionsAreImmutable() {
        ToothCondition condition = new ToothCondition(
                java.util.UUID.randomUUID(), "26", "MOD",
                ToothCondition.Condition.CARIES, null, null);
        condition.resolve();

        assertThat(condition.getResolvedAt()).isNotNull();
        assertThatThrownBy(() -> condition.edit("O", ToothCondition.Condition.CARIES, null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(condition::resolve)
                .isInstanceOf(InvalidRequestException.class);
    }
}
