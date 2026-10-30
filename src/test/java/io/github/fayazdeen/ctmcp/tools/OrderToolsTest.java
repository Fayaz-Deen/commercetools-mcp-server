package io.github.fayazdeen.ctmcp.tools;

import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.fixture;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.ok;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.page;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class OrderToolsTest {

    private static final String CART_ID = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
    private static final String ORDER_ID = "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e";

    private final FakeCommercetools ct = new FakeCommercetools();
    private final OrderTools tools = new OrderTools(ct.api);

    @Test
    void getCart_returnsTotalsAndLineItemsWithMaskedEmail() {
        ct.respond(ok(fixture("cart.json")));

        var cart = tools.getCart(CART_ID, null);

        assertThat(ct.lastUrl()).isEqualTo("/demo/carts/" + CART_ID);
        assertThat(cart.state()).isEqualTo("Active");
        assertThat(cart.total()).isEqualTo("259.98 EUR");
        assertThat(cart.totalQuantity()).isEqualTo(2);
        assertThat(cart.customerEmail()).isEqualTo("j***@example.com");
        var line = cart.lineItems().get(0);
        assertThat(line.sku()).isEqualTo("TR-42-BLK");
        assertThat(line.unitPrice()).isEqualTo("129.99 EUR");
        assertThat(line.total()).isEqualTo("259.98 EUR");
    }

    @Test
    void getCart_validatesIdAndReportsNotFound() {
        assertThatThrownBy(() -> tools.getCart("cart-1", null)).hasMessageContaining("'id' must be");
        verifyNoInteractions(ct.http);

        ct.respond(status(404, "{\"statusCode\":404,\"message\":\"missing\",\"errors\":[]}"));
        assertThatThrownBy(() -> tools.getCart(CART_ID, null))
                .isInstanceOf(NoSuchElementException.class).hasMessage("cart " + CART_ID + " not found");
    }

    @Test
    void getOrder_byIdHidesStreetAndPhone() {
        ct.respond(ok(fixture("order.json")));

        var order = tools.getOrder(ORDER_ID, null, null);

        assertThat(ct.lastUrl()).isEqualTo("/demo/orders/" + ORDER_ID);
        assertThat(order.orderNumber()).isEqualTo("ORD-10042");
        assertThat(order.orderState()).isEqualTo("Confirmed");
        assertThat(order.paymentState()).isEqualTo("Paid");
        assertThat(order.shipmentState()).isEqualTo("Shipped");
        assertThat(order.shipTo()).isEqualTo("Berlin, DE");
        assertThat(order.customerEmail()).isEqualTo("j***@example.com");
        assertThat(order.toString()).doesNotContain("Examplestrasse", "10115", "+49", "jane.doe");
    }

    @Test
    void getOrder_byOrderNumber() {
        ct.respond(ok(fixture("order.json")));

        tools.getOrder(null, "ORD-10042", null);

        assertThat(ct.lastUrl()).isEqualTo("/demo/orders/order-number=ORD-10042");
    }

    @Test
    void getOrder_requiresExactlyOneIdentifier() {
        assertThatThrownBy(() -> tools.getOrder(ORDER_ID, "ORD-10042", null))
                .hasMessageContaining("exactly one of: id, orderNumber");
        verifyNoInteractions(ct.http);
    }

    @Test
    void listRecentOrders_newestFirst() {
        ct.respond(ok(page(fixture("order.json"))));

        var result = tools.listRecentOrders(null, null);

        assertThat(ct.lastUrl()).startsWith("/demo/orders?").contains("sort=createdAt desc", "limit=10")
                .doesNotContain("where=");
        var summary = result.results().get(0);
        assertThat(summary.orderNumber()).isEqualTo("ORD-10042");
        assertThat(summary.total()).isEqualTo("129.99 EUR");
        assertThat(summary.lineItemCount()).isEqualTo(1);
    }

    @Test
    void listRecentOrders_filtersByCustomerEmailViaPredicateVariable() {
        ct.respond(ok(page()));

        tools.listRecentOrders(5, "jane.doe@example.com");

        assertThat(ct.lastUrl()).contains("where=customerEmail = :email", "var.email=jane.doe@example.com",
                "limit=5");
    }

    @Test
    void listRecentOrders_rejectsBadEmailAndLimit() {
        assertThatThrownBy(() -> tools.listRecentOrders(null, "not-an-email")).hasMessageContaining("email");
        assertThatThrownBy(() -> tools.listRecentOrders(0, null)).hasMessageContaining("between 1 and 50");
        verifyNoInteractions(ct.http);
    }

    @Test
    void listDiscountCodes_activeOnly() {
        ct.respond(ok(page("""
                {"id":"77776666-5555-4444-8333-222211110000","code":"SPRING10","name":{"en":"Spring sale"},
                 "isActive":true,"cartPredicate":"totalPrice > \\"50.00 EUR\\"","maxApplications":1000,
                 "cartDiscounts":[{"typeId":"cart-discount","id":"12341234-1234-4123-8123-123412341234"}],
                 "validUntil":"2026-06-30T23:59:59.000Z"}
                """)));

        var result = tools.listDiscountCodes(true, null, null);

        assertThat(ct.lastUrl()).startsWith("/demo/discount-codes?")
                .contains("where=isActive = true", "sort=createdAt desc");
        var code = result.results().get(0);
        assertThat(code.code()).isEqualTo("SPRING10");
        assertThat(code.name()).isEqualTo("Spring sale");
        assertThat(code.cartPredicate()).isEqualTo("totalPrice > \"50.00 EUR\"");
        assertThat(code.cartDiscountIds()).containsExactly("12341234-1234-4123-8123-123412341234");
    }

    @Test
    void listDiscountCodes_allByDefault() {
        ct.respond(ok(page()));

        tools.listDiscountCodes(null, 3, null);

        assertThat(ct.lastUrl()).doesNotContain("where=").contains("limit=3");
    }
}
