package io.github.fayazdeen.ctmcp.tools;

import com.commercetools.api.client.ByProjectKeyDiscountCodesGet;
import com.commercetools.api.client.ByProjectKeyOrdersGet;
import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.discount_code.DiscountCodePagedQueryResponse;
import com.commercetools.api.models.order.Order;
import com.commercetools.api.models.order.OrderPagedQueryResponse;
import io.github.fayazdeen.ctmcp.tools.Views.CartView;
import io.github.fayazdeen.ctmcp.tools.Views.DiscountCodeView;
import io.github.fayazdeen.ctmcp.tools.Views.OrderSummary;
import io.github.fayazdeen.ctmcp.tools.Views.OrderView;
import io.github.fayazdeen.ctmcp.tools.Views.Page;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Read-only cart, order and discount code tools. Customer emails are masked in every result. */
public class OrderTools {

    private final ProjectApiRoot api;

    public OrderTools(ProjectApiRoot api) {
        this.api = api;
    }

    @Tool(name = "get_cart", description = "Get a cart by id: state, line items, totals and quantities.")
    public CartView getCart(
            @ToolParam(description = "Cart id (UUID)") String id,
            @ToolParam(description = "Display locale, e.g. 'en'. Default 'en'", required = false) String locale) {
        String cartId = Inputs.id("id", id);
        String loc = Inputs.locale(locale);
        return Views.cart(CtCalls.call("cart " + cartId, "view_orders",
                () -> api.carts().withId(cartId).get().executeBlocking().getBody()), loc);
    }

    @Tool(name = "get_order", description = "Get an order with its states, line items and totals."
            + " Provide exactly one of id or orderNumber.")
    public OrderView getOrder(
            @ToolParam(description = "Order id (UUID)", required = false) String id,
            @ToolParam(description = "Order number, e.g. 'ORD-10042'", required = false) String orderNumber,
            @ToolParam(description = "Display locale, e.g. 'en'. Default 'en'", required = false) String locale) {
        Inputs.exactlyOne("id, orderNumber", id, orderNumber);
        String loc = Inputs.locale(locale);
        Order order;
        if (Inputs.present(id)) {
            String orderId = Inputs.id("id", id);
            order = CtCalls.call("order " + orderId, "view_orders",
                    () -> api.orders().withId(orderId).get().executeBlocking().getBody());
        } else {
            String number = Inputs.text("orderNumber", orderNumber);
            order = CtCalls.call("order " + number, "view_orders",
                    () -> api.orders().withOrderNumber(number).get().executeBlocking().getBody());
        }
        return Views.order(order, loc);
    }

    @Tool(name = "list_recent_orders", description = "List the most recent orders, newest first."
            + " Optionally only the orders of one customer email.")
    public Page<OrderSummary> listRecentOrders(
            @ToolParam(description = "Max results, 1-50. Default 10", required = false) Integer limit,
            @ToolParam(description = "Only orders placed with this customer email", required = false)
            String customerEmail) {
        int max = Inputs.limit(limit);
        String email = Inputs.present(customerEmail) ? Inputs.email("customerEmail", customerEmail) : null;
        OrderPagedQueryResponse response = CtCalls.call("orders", "view_orders", () -> {
            ByProjectKeyOrdersGet request = api.orders().get().withSort("createdAt desc").withLimit(max);
            if (email != null) {
                request = request.withWhere("customerEmail = :email").withPredicateVar("email", email);
            }
            return request.executeBlocking().getBody();
        });
        return new Page<>(response.getTotal(), response.getResults().stream().map(Views::orderSummary).toList());
    }

    @Tool(name = "list_discount_codes", description = "List discount codes, newest first, with validity,"
            + " usage limits and the cart predicate they apply to.")
    public Page<DiscountCodeView> listDiscountCodes(
            @ToolParam(description = "Only active codes. Default false", required = false) Boolean activeOnly,
            @ToolParam(description = "Max results, 1-50. Default 10", required = false) Integer limit,
            @ToolParam(description = "Display locale, e.g. 'en'. Default 'en'", required = false) String locale) {
        int max = Inputs.limit(limit);
        String loc = Inputs.locale(locale);
        DiscountCodePagedQueryResponse response = CtCalls.call("discount codes", "view_discount_codes", () -> {
            ByProjectKeyDiscountCodesGet request = api.discountCodes().get().withSort("createdAt desc").withLimit(max);
            if (Boolean.TRUE.equals(activeOnly)) {
                request = request.withWhere("isActive = true");
            }
            return request.executeBlocking().getBody();
        });
        return new Page<>(response.getTotal(),
                response.getResults().stream().map(d -> Views.discountCode(d, loc)).toList());
    }
}
