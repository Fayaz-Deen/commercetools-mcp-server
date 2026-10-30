package io.github.fayazdeen.ctmcp.tools;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

import com.commercetools.api.models.error.ErrorResponse;
import io.vrap.rmf.base.client.ApiHttpException;
import io.vrap.rmf.base.client.error.NotFoundException;

/** Runs a commercetools request and turns HTTP failures into short, actionable tool errors. */
final class CtCalls {

    private static final int MAX_DETAIL = 300;

    private CtCalls() {
    }

    /**
     * @param what  human name of the resource, used in the error message
     * @param scope the API client scope the call needs, shown when commercetools answers 403
     */
    static <T> T call(String what, String scope, Supplier<T> request) {
        try {
            return request.get();
        } catch (NotFoundException e) {
            throw new NoSuchElementException(what + " not found");
        } catch (ApiHttpException e) {
            throw new IllegalStateException(describe(what, scope, e));
        }
    }

    static String describe(String what, String scope, ApiHttpException e) {
        int status = e.getStatusCode();
        String hint = switch (status) {
            case 400 -> "the request was rejected" + detail(e);
            case 401 -> "authentication failed; check CTP_CLIENT_ID, CTP_CLIENT_SECRET and CTP_AUTH_URL";
            case 403 -> "the API client is missing the '" + scope + "' scope";
            case 409 -> "the resource was changed concurrently; fetch it again and retry";
            case 429 -> "rate limited by commercetools; retry later";
            default -> status >= 500 ? "commercetools is temporarily unavailable; retry later" : "unexpected error";
        };
        return "commercetools request for " + what + " failed (HTTP " + status + "): " + hint;
    }

    private static String detail(ApiHttpException e) {
        try {
            String message = e.getBodyAs(ErrorResponse.class).getMessage();
            if (message == null || message.isBlank()) {
                return "";
            }
            return ": " + (message.length() > MAX_DETAIL ? message.substring(0, MAX_DETAIL) + "..." : message);
        } catch (RuntimeException ignored) {
            return ""; // body is not a commercetools error document; the status alone is enough
        }
    }
}
