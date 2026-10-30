package io.github.fayazdeen.ctmcp.tools;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.commercetools.api.client.ByProjectKeyCategoriesGet;
import com.commercetools.api.client.ByProjectKeyInventoryGet;
import com.commercetools.api.client.ByProjectKeyProductProjectionsGet;
import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.category.CategoryPagedQueryResponse;
import com.commercetools.api.models.inventory.InventoryPagedQueryResponse;
import com.commercetools.api.models.product.ProductProjection;
import com.commercetools.api.models.product.ProductProjectionPagedSearchResponse;
import com.commercetools.api.models.product.ProductVariant;
import io.github.fayazdeen.ctmcp.tools.Views.CategoryView;
import io.github.fayazdeen.ctmcp.tools.Views.InventoryView;
import io.github.fayazdeen.ctmcp.tools.Views.Page;
import io.github.fayazdeen.ctmcp.tools.Views.PriceLookup;
import io.github.fayazdeen.ctmcp.tools.Views.ProductDetail;
import io.github.fayazdeen.ctmcp.tools.Views.ProductSummary;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Read-only product, price, category and inventory tools. */
public class CatalogTools {

    private static final String SKU_PREDICATE = "masterVariant(sku = :sku) or variants(sku = :sku)";

    private final ProjectApiRoot api;

    public CatalogTools(ProjectApiRoot api) {
        this.api = api;
    }

    @Tool(name = "search_products", description = "Full-text search over published products"
            + " (commercetools Product Projection Search). Returns id, name, SKU, price and stock per hit.")
    public Page<ProductSummary> searchProducts(
            @ToolParam(description = "Search text, e.g. 'running shoes'") String query,
            @ToolParam(description = "Locale to search and display in, e.g. 'en' or 'de-DE'. Default 'en'",
                    required = false) String locale,
            @ToolParam(description = "Max results, 1-50. Default 10", required = false) Integer limit) {
        String text = Inputs.text("query", query);
        String loc = Inputs.locale(locale);
        int max = Inputs.limit(limit);
        ProductProjectionPagedSearchResponse response = CtCalls.call("product search", "view_products",
                () -> api.productProjections().search().get()
                        .withText(loc, text)
                        .withStaged(false)
                        .withLimit(max)
                        .executeBlocking().getBody());
        return new Page<>(response.getTotal(),
                response.getResults().stream().map(p -> Views.summary(p, loc)).toList());
    }

    @Tool(name = "get_product", description = "Get one published product with its variants, prices,"
            + " availability and attributes. Provide exactly one of id, key or sku.")
    public ProductDetail getProduct(
            @ToolParam(description = "Product id (UUID)", required = false) String id,
            @ToolParam(description = "Product key", required = false) String key,
            @ToolParam(description = "SKU of any variant of the product", required = false) String sku,
            @ToolParam(description = "Display locale, e.g. 'en'. Default 'en'", required = false) String locale) {
        Inputs.exactlyOne("id, key, sku", id, key, sku);
        String loc = Inputs.locale(locale);
        ProductProjection product;
        if (Inputs.present(id)) {
            String productId = Inputs.id("id", id);
            product = CtCalls.call("product " + productId, "view_products",
                    () -> api.productProjections().withId(productId).get().executeBlocking().getBody());
        } else if (Inputs.present(key)) {
            String productKey = Inputs.key("key", key);
            product = CtCalls.call("product with key " + productKey, "view_products",
                    () -> api.productProjections().withKey(productKey).get().executeBlocking().getBody());
        } else {
            product = findBySku(Inputs.text("sku", sku), null, null);
        }
        return Views.detail(product, loc);
    }

    @Tool(name = "get_price", description = "Get the price commercetools selects for a SKU in a currency and"
            + " optional country, including any product discount.")
    public PriceLookup getPrice(
            @ToolParam(description = "Variant SKU") String sku,
            @ToolParam(description = "ISO 4217 currency, e.g. EUR") String currency,
            @ToolParam(description = "ISO 3166-1 alpha-2 country, e.g. DE", required = false) String country,
            @ToolParam(description = "Display locale, e.g. 'en'. Default 'en'", required = false) String locale) {
        String validSku = Inputs.text("sku", sku);
        String cur = Inputs.currency(currency);
        String ctry = Inputs.country(country);
        String loc = Inputs.locale(locale);
        ProductProjection product = findBySku(validSku, cur, ctry);
        ProductVariant variant = variantWithSku(product, validSku);
        boolean found = variant.getPrice() != null;
        return new PriceLookup(validSku, product.getId(), Views.pick(product.getName(), loc), cur, ctry,
                found ? Views.price(variant.getPrice()) : null,
                found ? null : "No price matches this currency/country. Use get_product to see all prices.");
    }

    @Tool(name = "list_categories", description = "List categories. Without parentId, lists top-level"
            + " categories; with parentId, lists that category's direct children.")
    public Page<CategoryView> listCategories(
            @ToolParam(description = "Parent category id (UUID)", required = false) String parentId,
            @ToolParam(description = "Display locale, e.g. 'en'. Default 'en'", required = false) String locale,
            @ToolParam(description = "Max results, 1-50. Default 10", required = false) Integer limit) {
        String loc = Inputs.locale(locale);
        int max = Inputs.limit(limit);
        String parent = Inputs.present(parentId) ? Inputs.id("parentId", parentId) : null;
        CategoryPagedQueryResponse response = CtCalls.call("categories", "view_categories", () -> {
            ByProjectKeyCategoriesGet request = api.categories().get().withSort("orderHint asc").withLimit(max);
            request = parent == null
                    ? request.withWhere("parent is not defined")
                    : request.withWhere("parent(id = :parentId)").withPredicateVar("parentId", parent);
            return request.executeBlocking().getBody();
        });
        return new Page<>(response.getTotal(),
                response.getResults().stream().map(c -> Views.category(c, loc)).toList());
    }

    @Tool(name = "get_inventory", description = "Get stock for a SKU: quantity on stock and available"
            + " quantity per supply channel. Optionally restrict to one channel by key.")
    public Page<InventoryView> getInventory(
            @ToolParam(description = "Variant SKU") String sku,
            @ToolParam(description = "Supply channel key, e.g. 'warehouse-berlin'", required = false)
            String channelKey) {
        String validSku = Inputs.text("sku", sku);
        String channelId = Inputs.present(channelKey) ? channelId(Inputs.key("channelKey", channelKey)) : null;
        InventoryPagedQueryResponse response = CtCalls.call("inventory", "view_products", () -> {
            ByProjectKeyInventoryGet request = api.inventory().get().withLimit(Inputs.MAX_LIMIT)
                    .withPredicateVar("sku", validSku);
            request = channelId == null
                    ? request.withWhere("sku = :sku")
                    : request.withWhere("sku = :sku and supplyChannel(id = :channelId)")
                            .withPredicateVar("channelId", channelId);
            return request.executeBlocking().getBody();
        });
        return new Page<>(response.getTotal(), response.getResults().stream().map(Views::inventory).toList());
    }

    private String channelId(String channelKey) {
        return CtCalls.call("channel with key " + channelKey, "view_products",
                () -> api.channels().withKey(channelKey).get().executeBlocking().getBody()).getId();
    }

    private ProductProjection findBySku(String sku, String currency, String country) {
        return CtCalls.call("product with sku " + sku, "view_products", () -> {
            ByProjectKeyProductProjectionsGet request = api.productProjections().get()
                    .withWhere(SKU_PREDICATE)
                    .withPredicateVar("sku", sku)
                    .withLimit(1);
            if (currency != null) {
                request = request.withPriceCurrency(currency);
            }
            if (country != null) {
                request = request.withPriceCountry(country);
            }
            return request.executeBlocking().getBody();
        }).getResults().stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("product with sku " + sku + " not found"));
    }

    private static ProductVariant variantWithSku(ProductProjection product, String sku) {
        Stream<ProductVariant> others = product.getVariants() == null ? Stream.empty() : product.getVariants().stream();
        return Stream.concat(Stream.of(product.getMasterVariant()), others)
                .filter(v -> sku.equals(v.getSku()))
                .findFirst()
                .orElse(product.getMasterVariant());
    }
}
