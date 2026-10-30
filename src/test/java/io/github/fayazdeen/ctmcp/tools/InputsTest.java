package io.github.fayazdeen.ctmcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InputsTest {

    @Test
    void id_acceptsUuidAndTrims() {
        assertThat(Inputs.id("id", " 5a8a0e1f-0b5c-4c63-9d1e-3f2a6b7c8d90 "))
                .isEqualTo("5a8a0e1f-0b5c-4c63-9d1e-3f2a6b7c8d90");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "5a8a0e1f0b5c4c639d1e3f2a6b7c8d90", "5a8a0e1f-0b5c-4c63-9d1e-3f2a6b7c8d9z"})
    void id_rejectsNonUuid(String value) {
        assertThatThrownBy(() -> Inputs.id("id", value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "has space", "semi;colon", "quote\""})
    void key_rejectsInvalidKeys(String value) {
        assertThatThrownBy(() -> Inputs.key("key", value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void text_rejectsControlCharactersAndOverlongValues() {
        assertThatThrownBy(() -> Inputs.text("sku", "AB\u0000C")).hasMessageContaining("control characters");
        assertThatThrownBy(() -> Inputs.text("sku", "x".repeat(257))).hasMessageContaining("at most 256");
        assertThat(Inputs.text("sku", "x".repeat(256))).hasSize(256);
    }

    @Test
    void locale_defaultsAndValidates() {
        assertThat(Inputs.locale(null)).isEqualTo("en");
        assertThat(Inputs.locale("de-DE")).isEqualTo("de-DE");
        assertThatThrownBy(() -> Inputs.locale("de_DE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limit_defaultsAndEnforcesRange() {
        assertThat(Inputs.limit(null)).isEqualTo(Inputs.DEFAULT_LIMIT);
        assertThat(Inputs.limit(50)).isEqualTo(50);
        assertThatThrownBy(() -> Inputs.limit(51)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Inputs.limit(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currencyAndCountry() {
        assertThat(Inputs.currency("EUR")).isEqualTo("EUR");
        assertThat(Inputs.country(null)).isNull();
        assertThat(Inputs.country("DE")).isEqualTo("DE");
        assertThatThrownBy(() -> Inputs.currency("eur")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Inputs.country("DEU")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exactlyOne_countsNonBlankValues() {
        Inputs.exactlyOne("a, b", "x", " ");
        assertThatThrownBy(() -> Inputs.exactlyOne("a, b", null, "")).hasMessage("Provide exactly one of: a, b");
        assertThatThrownBy(() -> Inputs.exactlyOne("a, b", "x", "y")).hasMessage("Provide exactly one of: a, b");
    }

    @Test
    void maskEmail() {
        assertThat(Views.maskEmail("jane.doe@example.com")).isEqualTo("j***@example.com");
        assertThat(Views.maskEmail("@example.com")).isEqualTo("***");
        assertThat(Views.maskEmail(null)).isNull();
    }
}
