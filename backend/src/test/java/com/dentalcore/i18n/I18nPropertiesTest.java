package com.dentalcore.i18n;

import com.dentalcore.infrastructure.i18n.I18nProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Startup fail-fast validation of the instance default language. */
class I18nPropertiesTest {

    @Test
    void acceptsSupportedLanguages() {
        assertThat(new I18nProperties("en").defaultLanguage()).isEqualTo("en");
        assertThat(new I18nProperties("es").defaultLanguage()).isEqualTo("es");
    }

    @Test
    void rejectsUnsupportedOrMissingValues() {
        assertThatThrownBy(() -> new I18nProperties("fr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default-language");
        assertThatThrownBy(() -> new I18nProperties("EN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new I18nProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
