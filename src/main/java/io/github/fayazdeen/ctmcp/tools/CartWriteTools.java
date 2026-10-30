package io.github.fayazdeen.ctmcp.tools;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.cart.Cart;
import com.commercetools.api.models.cart.CartAddLineItemActionBuilder;
import com.commercetools.api.models.cart.CartUpdateBuilder;
import io.github.fayazdeen.ctmcp.tools.Views.CartView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Write tools. Only registered when CT_MCP_ALLOW_WRITES=true. */
public class CartWriteTools {

    static final int MAX_QUANTITY = 99;

    private final ProjectApiRoot api;

    public CartWriteTools(ProjectApiRoot api) {
        this.api = api;
    }

    @Tool(name = "add_line_item_to_cart", description = "WRITE: add a SKU to an existing cart and return the"
            + " updated cart. Changes data in commercetools.")
    public CartView addLineItemToCart(
            @ToolParam(description = "Cart id (UUID)") String cartId,
            @ToolParam(description = "Variant SKU to add") String sku,
            @ToolParam(description = "Quantity, 1-99. Default 1", required = false) Integer quantity) {
        String id = Inputs.id("cartId", cartId);
        String validSku = Inputs.text("sku", sku);
        long qty = Inputs.range("quantity", quantity, 1, 1, MAX_QUANTITY);
        Cart current = CtCalls.call("cart " + id, "manage_orders",
                () -> api.carts().withId(id).get().executeBlocking().getBody());
        Cart updated = CtCalls.call("cart " + id, "manage_orders",
                () -> api.carts().withId(id)
                        .post(CartUpdateBuilder.of()
                                .version(current.getVersion())
                                .actions(CartAddLineItemActionBuilder.of().sku(validSku).quantity(qty).build())
                                .build())
                        .executeBlocking().getBody());
        return Views.cart(updated, Inputs.DEFAULT_LOCALE);
    }
}
