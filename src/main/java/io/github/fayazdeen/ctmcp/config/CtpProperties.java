package io.github.fayazdeen.ctmcp.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * commercetools API client settings, bound from the CTP_* environment variables
 * (the same names the Merchant Center uses in its ".env" download).
 * Construction fails fast with a readable message when a required value is missing.
 */
@ConfigurationProperties(prefix = "ctp")
public record CtpProperties(
        String projectKey,
        String clientId,
        String clientSecret,
        String authUrl,
        String apiUrl,
        String scopes) {

    public CtpProperties {
        List<String> missing = new ArrayList<>();
        if (isBlank(projectKey)) missing.add("CTP_PROJECT_KEY");
        if (isBlank(clientId)) missing.add("CTP_CLIENT_ID");
        if (isBlank(clientSecret)) missing.add("CTP_CLIENT_SECRET");
        if (isBlank(authUrl)) missing.add("CTP_AUTH_URL");
        if (isBlank(apiUrl)) missing.add("CTP_API_URL");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variable(s): " + String.join(", ", missing)
                    + ". Create an API client in the commercetools Merchant Center and set these values"
                    + " (see .env.example).");
        }
        requireHttpUrl("CTP_AUTH_URL", authUrl);
        requireHttpUrl("CTP_API_URL", apiUrl);
        authUrl = stripTrailingSlash(authUrl.strip());
        apiUrl = stripTrailingSlash(apiUrl.strip());
        scopes = isBlank(scopes) ? null : scopes.strip();
    }

    /** OAuth token endpoint derived from CTP_AUTH_URL. */
    public String tokenUrl() {
        return authUrl + "/oauth/token";
    }

    @Override
    public String toString() {
        // Never expose the client secret, even in debug output.
        return "CtpProperties[projectKey=" + projectKey + ", clientId=" + clientId + ", clientSecret=****, authUrl="
                + authUrl + ", apiUrl=" + apiUrl + ", scopes=" + scopes + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireHttpUrl(String name, String value) {
        String v = value.strip();
        if (!v.startsWith("https://") && !v.startsWith("http://")) {
            throw new IllegalStateException(name + " must be an http(s) URL, for example"
                    + " https://auth.europe-west1.gcp.commercetools.com");
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
