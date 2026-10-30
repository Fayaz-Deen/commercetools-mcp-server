package io.github.fayazdeen.ctmcp.tools;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validation for every value an AI client sends to a tool. Values only reach commercetools through
 * typed SDK calls or predicate variables, never by string concatenation into a query.
 */
final class Inputs {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final String DEFAULT_LOCALE = "en";

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{2,256}$");
    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_TEXT = 256;

    private Inputs() {
    }

    static String id(String name, String value) {
        String v = required(name, value);
        if (!UUID.matcher(v).matches()) {
            throw invalid(name, "must be a commercetools id (UUID)");
        }
        return v;
    }

    static String key(String name, String value) {
        String v = required(name, value);
        if (!KEY.matcher(v).matches()) {
            throw invalid(name, "must be 2-256 characters of letters, digits, '_' or '-'");
        }
        return v;
    }

    /** Free text such as a search term, SKU or order number. */
    static String text(String name, String value) {
        String v = required(name, value);
        if (v.length() > MAX_TEXT) {
            throw invalid(name, "must be at most " + MAX_TEXT + " characters");
        }
        if (v.chars().anyMatch(Character::isISOControl)) {
            throw invalid(name, "must not contain control characters");
        }
        return v;
    }

    static String email(String name, String value) {
        String v = text(name, value);
        if (!EMAIL.matcher(v).matches()) {
            throw invalid(name, "must be an email address");
        }
        return v;
    }

    static String locale(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String v = value.strip();
        if (!LOCALE.matcher(v).matches()) {
            throw invalid("locale", "must look like 'en' or 'en-US'");
        }
        return v;
    }

    static String currency(String value) {
        String v = required("currency", value);
        if (!CURRENCY.matcher(v).matches()) {
            throw invalid("currency", "must be an ISO 4217 code such as EUR or USD");
        }
        return v;
    }

    static String country(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (!COUNTRY.matcher(v).matches()) {
            throw invalid("country", "must be an ISO 3166-1 alpha-2 code such as DE or US");
        }
        return v;
    }

    static int limit(Integer value) {
        return range("limit", value, DEFAULT_LIMIT, 1, MAX_LIMIT);
    }

    static int range(String name, Integer value, int fallback, int min, int max) {
        int v = value == null ? fallback : value;
        if (v < min || v > max) {
            throw invalid(name, "must be between " + min + " and " + max);
        }
        return v;
    }

    /** Ensures that exactly one of several alternative identifiers was provided. */
    static void exactlyOne(String names, String... values) {
        long given = Arrays.stream(values).filter(Objects::nonNull).filter(v -> !v.isBlank()).count();
        if (given != 1) {
            throw new IllegalArgumentException("Provide exactly one of: " + names);
        }
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(name, "is required");
        }
        return value.strip();
    }

    private static IllegalArgumentException invalid(String name, String reason) {
        return new IllegalArgumentException("Invalid input: '" + name + "' " + reason);
    }
}
