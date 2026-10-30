package io.github.fayazdeen.ctmcp.tools;

import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.fixture;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.ok;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.page;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class CatalogToolsTest {

    private static final String PRODUCT_ID = "5a8a0e1f-0b5c-4c63-9d1e-3f2a6b7c8d90";

    private final FakeCommercetools ct = new FakeCommercetools();
    private final CatalogTools tools = new CatalogTools(ct.api);

    @Test
    void searchProducts_usesProductProjectionSearchAndReturnsSummaries() {
        ct.respond(ok(page(fixture("product-projection.json"))));

        var result = tools.searchProducts("trail shoe", "de", 5);

        assertThat(ct.lastUrl()).startsWith("/demo/product-projections/search?")
                .contains("text.de=trail shoe", "staged=false", "limit=5");
        assertThat(result.total()).isEqualTo(1);
        var hit = result.results().get(0);
        assertThat(hit.name()).isEqualTo("Trail-Laufschuh");
        assertThat(hit.sku()).isEqualTo("TR-42-BLK");
        assertThat(hit.price()).isEqualTo("129.99 EUR");
        assertThat(hit.onStock()).isTrue();
        assertThat(hit.image()).isEqualTo("https://images.example.test/tr-42-blk.jpg");
    }

    @Test
    void searchProducts_returnsConciseJsonThroughSpringAiToolCallback() {
        ct.respond(ok(page(fixture("product-projection.json"))));
        ToolCallback callback = Arrays.stream(MethodToolCallbackProvider.builder().toolObjects(tools).build()
                        .getToolCallbacks())
                .filter(c -> c.getToolDefinition().name().equals("search_products"))
                .findFirst().orElseThrow();

        String json = callback.call("{\"query\":\"trail\"}");

        assertThat(json).contains("\"sku\":\"TR-42-BLK\"", "\"price\":\"129.99 EUR\"")
                .doesNotContain("null", "\"variants\"", "\"prices\"");
    }

    @Test
    void searchProducts_rejectsInvalidInputWithoutCallingCommercetools() {
        assertThatThrownBy(() -> tools.searchProducts(" ", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("'query' is required");
        assertThatThrownBy(() -> tools.searchProducts("shoe", "english", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("locale");
        assertThatThrownBy(() -> tools.searchProducts("shoe", null, 500))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 50");
        verifyNoInteractions(ct.http);
    }

    @Test
    void getProduct_byId() {
        ct.respond(ok(fixture("product-projection.json")));

        var product = tools.getProduct(PRODUCT_ID, null, null, null);

        assertThat(ct.lastUrl()).startsWith("/demo/product-projections/" + PRODUCT_ID);
        assertThat(product.name()).isEqualTo("Trail Runner Shoe");
        assertThat(product.variantCount()).isEqualTo(2);
        var master = product.variants().get(0);
        assertThat(master.prices()).extracting(Views.PriceView::value).containsExactly("129.99 EUR", "139.99 USD");
        assertThat(master.prices().get(0).discounted()).isEqualTo("99.99 EUR");
        assertThat(master.attributes()).containsEntry("color", "black").containsEntry("size", 42L)
                .containsEntry("waterproof", true);
        assertThat(product.variants().get(1).onStock()).isFalse();
    }

    @Test
    void getProduct_byKey() {
        ct.respond(ok(fixture("product-projection.json")));

        var product = tools.getProduct(null, "trail-runner", null, "en");

        assertThat(ct.lastUrl()).startsWith("/demo/product-projections/key=trail-runner");
        assertThat(product.key()).isEqualTo("trail-runner");
    }

    @Test
    void getProduct_bySkuUsesPredicateVariableNotStringConcatenation() {
        ct.respond(ok(page(fixture("product-projection.json"))));

        tools.getProduct(null, null, "TR-43-BLK\" or id is defined", null);

        assertThat(ct.lastUrl())
                .contains("where=masterVariant(sku = :sku) or variants(sku = :sku)")
                .contains("var.sku=TR-43-BLK\" or id is defined");
    }

    @Test
    void getProduct_requiresExactlyOneIdentifier() {
        assertThatThrownBy(() -> tools.getProduct(null, null, null, null))
                .hasMessageContaining("exactly one of: id, key, sku");
        assertThatThrownBy(() -> tools.getProduct(PRODUCT_ID, "trail-runner", null, null))
                .hasMessageContaining("exactly one of");
        assertThatThrownBy(() -> tools.getProduct("not-a-uuid", null, null, null))
                .hasMessageContaining("'id' must be a commercetools id");
        verifyNoInteractions(ct.http);
    }

    @Test
    void getProduct_notFoundIsReportedClearly() {
        ct.respond(status(404, "{\"statusCode\":404,\"message\":\"not found\",\"errors\":[]}"));

        assertThatThrownBy(() -> tools.getProduct(PRODUCT_ID, null, null, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("product " + PRODUCT_ID + " not found");
    }

    @Test
    void getProduct_unknownSkuIsReportedClearly() {
        ct.respond(ok(page()));

        assertThatThrownBy(() -> tools.getProduct(null, null, "NOPE", null))
                .isInstanceOf(NoSuchElementException.class).hasMessageContaining("sku NOPE");
    }

    @Test
    void getProduct_forbiddenNamesTheMissingScope() {
        ct.respond(status(403, "{\"statusCode\":403,\"message\":\"Insufficient scope\",\"errors\":[]}"));

        assertThatThrownBy(() -> tools.getProduct(PRODUCT_ID, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 403").hasMessageContaining("'view_products' scope");
    }

    @Test
    void getPrice_usesPriceSelectionAndPicksTheMatchingVariant() {
        ct.respond(ok(page("""
                {"id":"%s","name":{"en":"Trail Runner Shoe"},
                 "masterVariant":{"id":1,"sku":"TR-42-BLK"},
                 "variants":[{"id":2,"sku":"TR-43-BLK","price":{"id":"p2",
                   "value":{"type":"centPrecision","currencyCode":"EUR","centAmount":11999,"fractionDigits":2},
                   "country":"DE"}}]}
                """.formatted(PRODUCT_ID))));

        var price = tools.getPrice("TR-43-BLK", "EUR", "DE", null);

        assertThat(ct.lastUrl()).contains("priceCurrency=EUR", "priceCountry=DE", "var.sku=TR-43-BLK");
        assertThat(price.selectedPrice().value()).isEqualTo("119.99 EUR");
        assertThat(price.note()).isNull();
    }

    @Test
    void getPrice_explainsWhenNoPriceMatches() {
        ct.respond(ok(page("{\"id\":\"%s\",\"masterVariant\":{\"id\":1,\"sku\":\"TR-42-BLK\"},\"variants\":[]}"
                .formatted(PRODUCT_ID))));

        var price = tools.getPrice("TR-42-BLK", "JPY", null, null);

        assertThat(price.selectedPrice()).isNull();
        assertThat(price.note()).contains("No price matches");
    }

    @Test
    void getPrice_validatesCurrencyAndCountry() {
        assertThatThrownBy(() -> tools.getPrice("TR-42-BLK", "euro", null, null)).hasMessageContaining("ISO 4217");
        assertThatThrownBy(() -> tools.getPrice("TR-42-BLK", "EUR", "Germany", null)).hasMessageContaining("3166");
        verifyNoInteractions(ct.http);
    }

    @Test
    void listCategories_topLevelByDefault() {
        ct.respond(ok(page("""
                {"id":"c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f","key":"shoes","name":{"en":"Shoes"},
                 "slug":{"en":"shoes"},"orderHint":"0.1"}
                """)));

        var result = tools.listCategories(null, null, null);

        assertThat(ct.lastUrl()).startsWith("/demo/categories?")
                .contains("where=parent is not defined", "sort=orderHint asc", "limit=10");
        assertThat(result.results()).extracting(Views.CategoryView::name).containsExactly("Shoes");
    }

    @Test
    void listCategories_childrenOfParent() {
        String parent = "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f";
        ct.respond(ok(page("""
                {"id":"aa11bb22-cc33-4d44-8e55-ff6677889900","name":{"en":"Trail"},"slug":{"en":"trail"},
                 "parent":{"typeId":"category","id":"%s"}}
                """.formatted(parent))));

        var result = tools.listCategories(parent, null, 20);

        assertThat(ct.lastUrl()).contains("where=parent(id = :parentId)", "var.parentId=" + parent, "limit=20");
        assertThat(result.results().get(0).parentId()).isEqualTo(parent);
    }

    @Test
    void getInventory_bySku() {
        ct.respond(ok(page("""
                {"id":"11112222-3333-4444-8555-666677778888","sku":"TR-42-BLK","quantityOnStock":40,
                 "availableQuantity":37,"supplyChannel":{"typeId":"channel","id":"99998888-7777-4666-8555-444433332222"}}
                """)));

        var result = tools.getInventory("TR-42-BLK", null);

        assertThat(ct.lastUrl()).startsWith("/demo/inventory?").contains("where=sku = :sku", "var.sku=TR-42-BLK");
        var entry = result.results().get(0);
        assertThat(entry.availableQuantity()).isEqualTo(37);
        assertThat(entry.channelId()).isEqualTo("99998888-7777-4666-8555-444433332222");
    }

    @Test
    void getInventory_resolvesChannelKeyFirst() {
        String channelId = "99998888-7777-4666-8555-444433332222";
        ct.respond(ok("{\"id\":\"" + channelId + "\",\"key\":\"warehouse-berlin\",\"roles\":[\"InventorySupply\"]}"),
                ok(page()));

        tools.getInventory("TR-42-BLK", "warehouse-berlin");

        var requests = ct.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getUri().getPath()).isEqualTo("/demo/channels/key=warehouse-berlin");
        assertThat(ct.lastUrl()).contains("where=sku = :sku and supplyChannel(id = :channelId)",
                "var.channelId=" + channelId);
    }

    @Test
    void getInventory_rejectsInvalidChannelKey() {
        assertThatThrownBy(() -> tools.getInventory("TR-42-BLK", "bad key!"))
                .hasMessageContaining("'channelKey'");
        verifyNoInteractions(ct.http);
    }
}
