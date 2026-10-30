package io.github.fayazdeen.ctmcp.tools;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.defaultconfig.ApiRootBuilder;
import io.vrap.rmf.base.client.ApiHttpHeaders;
import io.vrap.rmf.base.client.ApiHttpRequest;
import io.vrap.rmf.base.client.ApiHttpResponse;
import io.vrap.rmf.base.client.VrapHttpClient;
import org.mockito.ArgumentCaptor;

/**
 * A real commercetools SDK client whose HTTP layer is a Mockito mock. Tools run through the actual SDK
 * request builders and JSON deserialization; only the network is replaced by canned responses.
 */
final class FakeCommercetools {

    final VrapHttpClient http = mock(VrapHttpClient.class);
    // withErrorMiddleware mirrors defaultClient(): non-2xx responses become typed SDK exceptions.
    final ProjectApiRoot api = ApiRootBuilder.of(http)
            .withApiBaseUrl("https://api.example.test")
            .withErrorMiddleware()
            .build("demo");

    /** Queues responses; each request consumes the next one (the last one repeats). */
    FakeCommercetools respond(Response first, Response... rest) {
        var stubbing = when(http.execute(any())).thenReturn(first.future());
        for (Response r : rest) {
            stubbing = stubbing.thenReturn(r.future());
        }
        return this;
    }

    List<ApiHttpRequest> requests() {
        ArgumentCaptor<ApiHttpRequest> captor = ArgumentCaptor.forClass(ApiHttpRequest.class);
        verify(http, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues();
    }

    ApiHttpRequest lastRequest() {
        List<ApiHttpRequest> all = requests();
        return all.get(all.size() - 1);
    }

    /** Decoded path plus query of the last request, e.g. "/demo/orders?sort=createdAt desc&limit=10". */
    String lastUrl() {
        var uri = lastRequest().getUri();
        String query = uri.getRawQuery() == null ? "" : "?" + URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        return URLDecoder.decode(uri.getRawPath(), StandardCharsets.UTF_8) + query;
    }

    static Response ok(String json) {
        return new Response(200, json);
    }

    static Response status(int status, String json) {
        return new Response(status, json);
    }

    /** A commercetools paged query response wrapping the given JSON documents. */
    static String page(String... results) {
        return "{\"limit\":10,\"offset\":0,\"count\":" + results.length + ",\"total\":" + results.length
                + ",\"results\":[" + String.join(",", Arrays.asList(results)) + "]}";
    }

    static String fixture(String name) {
        try (InputStream in = FakeCommercetools.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Response(int status, String json) {
        CompletableFuture<ApiHttpResponse<byte[]>> future() {
            return CompletableFuture.completedFuture(
                    new ApiHttpResponse<>(status, new ApiHttpHeaders(), json.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
