package io.github.fayazdeen.ctmcp.tools;

import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.fixture;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.ok;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;

import io.vrap.rmf.base.client.ApiHttpMethod;
import org.junit.jupiter.api.Test;

class CartWriteToolsTest {

    private static final String CART_ID = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";

    private final FakeCommercetools ct = new FakeCommercetools();
    private final CartWriteTools tools = new CartWriteTools(ct.api);

    @Test
    void addLineItem_postsAddLineItemActionWithCurrentVersion() {
        ct.respond(ok(fixture("cart.json")), ok(fixture("cart.json")));

        var cart = tools.addLineItemToCart(CART_ID, "TR-43-BLK", 3);

        var requests = ct.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getMethod()).isEqualTo(ApiHttpMethod.GET);
        var update = requests.get(1);
        assertThat(update.getMethod()).isEqualTo(ApiHttpMethod.POST);
        assertThat(update.getUri().getPath()).isEqualTo("/demo/carts/" + CART_ID);
        assertThat(new String(update.getBody(), StandardCharsets.UTF_8))
                .contains("\"version\":7", "\"action\":\"addLineItem\"", "\"sku\":\"TR-43-BLK\"", "\"quantity\":3");
        assertThat(cart.id()).isEqualTo(CART_ID);
    }

    @Test
    void addLineItem_defaultsQuantityToOne() {
        ct.respond(ok(fixture("cart.json")), ok(fixture("cart.json")));

        tools.addLineItemToCart(CART_ID, "TR-43-BLK", null);

        assertThat(new String(ct.lastRequest().getBody(), StandardCharsets.UTF_8)).contains("\"quantity\":1");
    }

    @Test
    void addLineItem_validatesInput() {
        assertThatThrownBy(() -> tools.addLineItemToCart("x", "TR-43-BLK", 1)).hasMessageContaining("'cartId'");
        assertThatThrownBy(() -> tools.addLineItemToCart(CART_ID, "", 1)).hasMessageContaining("'sku' is required");
        assertThatThrownBy(() -> tools.addLineItemToCart(CART_ID, "TR-43-BLK", 100))
                .hasMessageContaining("'quantity' must be between 1 and 99");
        verifyNoInteractions(ct.http);
    }

    @Test
    void addLineItem_concurrentModificationAsksForRetry() {
        ct.respond(ok(fixture("cart.json")),
                status(409, "{\"statusCode\":409,\"message\":\"Version mismatch\",\"errors\":[]}"));

        assertThatThrownBy(() -> tools.addLineItemToCart(CART_ID, "TR-43-BLK", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 409").hasMessageContaining("retry");
    }
}
