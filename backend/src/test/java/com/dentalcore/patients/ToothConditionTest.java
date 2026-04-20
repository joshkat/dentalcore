package com.dentalcore.patients;

import com.dentalcore.patients.internal.entity.ToothCondition;
import com.dentalcore.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToothConditionTest {

    @Test
    void permanentAndPrimaryTeethAreAccepted() {
        assertThat(ToothCondition.normalizeTooth("1")).isEqualTo("1");
        assertThat(ToothCondition.normalizeTooth("32")).isEqualTo("32");
        assertThat(ToothCondition.normalizeTooth("a")).isEqualTo("A");
        assertThat(ToothCondition.normalizeTooth("T")).isEqualTo("T");
    }

    @Test
    void invalidTeethAreRejected() {
        for (String bad : new String[]{"0", "33", "U", "Z", "1A", "", "  "}) {
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
                java.util.UUID.randomUUID(), "14", "MOD",
                ToothCondition.Condition.CARIES, null, null);
        condition.resolve();

        assertThat(condition.getResolvedAt()).isNotNull();
        assertThatThrownBy(() -> condition.edit("O", ToothCondition.Condition.CARIES, null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(condition::resolve)
                .isInstanceOf(InvalidRequestException.class);
    }
}
