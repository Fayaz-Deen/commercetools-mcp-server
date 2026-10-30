package io.github.fayazdeen.ctmcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CtpPropertiesTest {

    private static final String AUTH = "https://auth.europe-west1.gcp.commercetools.com";
    private static final String API = "https://api.europe-west1.gcp.commercetools.com";

    @Test
    void listsEveryMissingVariableInOneMessage() {
        assertThatThrownBy(() -> new CtpProperties("demo", "", null, AUTH, " ", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CTP_CLIENT_ID, CTP_CLIENT_SECRET, CTP_API_URL")
                .hasMessageNotContaining("CTP_PROJECT_KEY")
                .hasMessageContaining(".env.example");
    }

    @Test
    void rejectsNonHttpUrls() {
        assertThatThrownBy(() -> new CtpProperties("demo", "id", "secret", "auth.example.test", API, null))
                .hasMessageContaining("CTP_AUTH_URL must be an http(s) URL");
    }

    @Test
    void normalisesUrlsAndScopes() {
        var props = new CtpProperties("demo", "id", "secret", AUTH + "/", API + "/", " ");

        assertThat(props.tokenUrl()).isEqualTo(AUTH + "/oauth/token");
        assertThat(props.apiUrl()).isEqualTo(API);
        assertThat(props.scopes()).isNull();
    }

    @Test
    void toStringNeverContainsTheSecret() {
        var props = new CtpProperties("demo", "id", "placeholder-secret-value", AUTH, API, "view_products:demo");

        assertThat(props.toString()).doesNotContain("placeholder-secret-value").contains("clientSecret=****");
    }
}
