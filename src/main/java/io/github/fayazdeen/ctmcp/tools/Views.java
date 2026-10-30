package io.github.fayazdeen.ctmcp.tools;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.commercetools.api.models.cart.Cart;
import com.commercetools.api.models.cart.LineItem;
import com.commercetools.api.models.category.Category;
import com.commercetools.api.models.common.Address;
import com.commercetools.api.models.common.Image;
import com.commercetools.api.models.common.LocalizedString;
import com.commercetools.api.models.common.Price;
import com.commercetools.api.models.common.Reference;
import com.commercetools.api.models.common.TypedMoney;
import com.commercetools.api.models.customer.Customer;
import com.commercetools.api.models.discount_code.DiscountCode;
import com.commercetools.api.models.inventory.InventoryEntry;
import com.commercetools.api.models.order.Order;
import com.commercetools.api.models.product.Attribute;
import com.commercetools.api.models.product.ProductProjection;
import com.commercetools.api.models.product.ProductVariant;
import com.commercetools.api.models.product.ProductVariantAvailability;
import com.commercetools.api.models.product_type.AttributeLocalizedEnumValue;
import com.commercetools.api.models.product_type.AttributePlainEnumValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.vrap.rmf.base.client.JsonEnum;

/**
 * Concise, allow-listed views of commercetools resources. Only the fields declared here ever reach the
 * AI client, so new or sensitive fields on the API side (password hashes, full addresses) stay out.
 */
final class Views {

    static final int MAX_VARIANTS = 25;
    static final int MAX_PRICES = 10;

    private Views() {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Page<T>(Long total, List<T> results) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record ProductSummary(String id, String key, String name, String slug, String sku, String price,
            Boolean onStock, String image) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record ProductDetail(String id, String key, String name, String slug, String description,
            String productTypeId, List<String> categoryIds, Boolean published, String lastModifiedAt,
            int variantCount, List<VariantView> variants) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record VariantView(Long id, String sku, String key, List<PriceView> prices, Boolean onStock,
            Long availableQuantity, List<String> images, Map<String, Object> attributes) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record PriceView(String value, String discounted, String country, String channelId, String customerGroupId,
            String validFrom, String validUntil) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record PriceLookup(String sku, String productId, String productName, String currency, String country,
            PriceView selectedPrice, String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record CategoryView(String id, String key, String name, String slug, String parentId, String orderHint) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record InventoryView(String sku, String channelId, Long quantityOnStock, Long availableQuantity,
            Long restockableInDays, String expectedDelivery) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record LineItemView(String sku, String name, Long quantity, String unitPrice, String total) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record CartView(String id, Long version, String state, String customerId, String customerEmail,
            String country, String total, Long totalQuantity, List<LineItemView> lineItems,
            String createdAt, String lastModifiedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record OrderView(String id, String orderNumber, String createdAt, String orderState, String paymentState,
            String shipmentState, String customerId, String customerEmail, String total,
            List<LineItemView> lineItems, String shipTo) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record OrderSummary(String id, String orderNumber, String createdAt, String orderState,
            String paymentState, String customerEmail, String total, int lineItemCount) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record CustomerView(String id, String key, String customerNumber, String email, String firstName,
            String lastName, Boolean emailVerified, String customerGroupId, List<String> stores,
            int addressCount, List<String> addressCountries, String createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record DiscountCodeView(String id, String code, String name, Boolean active, String validFrom,
            String validUntil, Long maxApplications, Long maxApplicationsPerCustomer, String cartPredicate,
            List<String> cartDiscountIds) {
    }

    // ---- mappers -------------------------------------------------------------------------------

    static ProductSummary summary(ProductProjection p, String locale) {
        ProductVariant master = p.getMasterVariant();
        Price price = master.getPrice() != null ? master.getPrice() : first(master.getPrices());
        return new ProductSummary(p.getId(), p.getKey(), pick(p.getName(), locale), pick(p.getSlug(), locale),
                master.getSku(), price == null ? null : money(price.getValue()), onStock(master.getAvailability()),
                master.getImages() == null || master.getImages().isEmpty() ? null : master.getImages().get(0).getUrl());
    }

    static ProductDetail detail(ProductProjection p, String locale) {
        List<ProductVariant> all = new java.util.ArrayList<>();
        all.add(p.getMasterVariant());
        if (p.getVariants() != null) {
            all.addAll(p.getVariants());
        }
        return new ProductDetail(p.getId(), p.getKey(), pick(p.getName(), locale), pick(p.getSlug(), locale),
                pick(p.getDescription(), locale), refId(p.getProductType()), ids(p.getCategories()),
                p.getPublished(), str(p.getLastModifiedAt()), all.size(),
                all.stream().limit(MAX_VARIANTS).map(v -> variant(v, locale)).toList());
    }

    static VariantView variant(ProductVariant v, String locale) {
        ProductVariantAvailability availability = v.getAvailability();
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (v.getAttributes() != null) {
            for (Attribute a : v.getAttributes()) {
                attributes.put(a.getName(), simplify(a.getValue(), locale));
            }
        }
        return new VariantView(v.getId(), v.getSku(), v.getKey(),
                map(v.getPrices(), Views::price, MAX_PRICES), onStock(availability),
                availability == null ? null : availability.getAvailableQuantity(),
                map(v.getImages(), Image::getUrl, MAX_PRICES), attributes);
    }

    static PriceView price(Price p) {
        return new PriceView(money(p.getValue()), p.getDiscounted() == null ? null : money(p.getDiscounted().getValue()),
                p.getCountry(), refId(p.getChannel()), refId(p.getCustomerGroup()), str(p.getValidFrom()),
                str(p.getValidUntil()));
    }

    static CategoryView category(Category c, String locale) {
        return new CategoryView(c.getId(), c.getKey(), pick(c.getName(), locale), pick(c.getSlug(), locale),
                refId(c.getParent()), c.getOrderHint());
    }

    static InventoryView inventory(InventoryEntry e) {
        return new InventoryView(e.getSku(), refId(e.getSupplyChannel()), e.getQuantityOnStock(),
                e.getAvailableQuantity(), e.getRestockableInDays(), str(e.getExpectedDelivery()));
    }

    static LineItemView lineItem(LineItem li, String locale) {
        return new LineItemView(li.getVariant() == null ? null : li.getVariant().getSku(), pick(li.getName(), locale),
                li.getQuantity(), li.getPrice() == null ? null : money(li.getPrice().getValue()),
                money(li.getTotalPrice()));
    }

    static CartView cart(Cart c, String locale) {
        return new CartView(c.getId(), c.getVersion(), enumName(c.getCartState()), c.getCustomerId(),
                maskEmail(c.getCustomerEmail()), c.getCountry(), money(c.getTotalPrice()), c.getTotalLineItemQuantity(),
                map(c.getLineItems(), li -> lineItem(li, locale), Integer.MAX_VALUE), str(c.getCreatedAt()),
                str(c.getLastModifiedAt()));
    }

    static OrderView order(Order o, String locale) {
        return new OrderView(o.getId(), o.getOrderNumber(), str(o.getCreatedAt()), enumName(o.getOrderState()),
                enumName(o.getPaymentState()), enumName(o.getShipmentState()), o.getCustomerId(),
                maskEmail(o.getCustomerEmail()), money(o.getTotalPrice()),
                map(o.getLineItems(), li -> lineItem(li, locale), Integer.MAX_VALUE), shipTo(o.getShippingAddress()));
    }

    static OrderSummary orderSummary(Order o) {
        return new OrderSummary(o.getId(), o.getOrderNumber(), str(o.getCreatedAt()), enumName(o.getOrderState()),
                enumName(o.getPaymentState()), maskEmail(o.getCustomerEmail()), money(o.getTotalPrice()),
                o.getLineItems() == null ? 0 : o.getLineItems().size());
    }

    static CustomerView customer(Customer c) {
        List<Address> addresses = c.getAddresses() == null ? List.of() : c.getAddresses();
        return new CustomerView(c.getId(), c.getKey(), c.getCustomerNumber(), maskEmail(c.getEmail()),
                c.getFirstName(), initial(c.getLastName()), c.getIsEmailVerified(), refId(c.getCustomerGroup()),
                c.getStores() == null ? null : c.getStores().stream().map(s -> s.getKey()).toList(),
                addresses.size(), addresses.stream().map(Address::getCountry).filter(Objects::nonNull).distinct().toList(),
                str(c.getCreatedAt()));
    }

    static DiscountCodeView discountCode(DiscountCode d, String locale) {
        return new DiscountCodeView(d.getId(), d.getCode(), pick(d.getName(), locale), d.getIsActive(),
                str(d.getValidFrom()), str(d.getValidUntil()), d.getMaxApplications(),
                d.getMaxApplicationsPerCustomer(), d.getCartPredicate(), ids(d.getCartDiscounts()));
    }

    // ---- small helpers -------------------------------------------------------------------------

    /** Value in the requested locale, falling back to the language part, then to any available value. */
    static String pick(LocalizedString text, String locale) {
        if (text == null || text.values() == null || text.values().isEmpty()) {
            return null;
        }
        Map<String, String> values = text.values();
        if (values.containsKey(locale)) {
            return values.get(locale);
        }
        String language = locale.split("-")[0];
        return values.entrySet().stream()
                .filter(e -> e.getKey().equals(language) || e.getKey().startsWith(language + "-"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(values.values().iterator().next());
    }

    /** "19.99 EUR" from cent amount and fraction digits. */
    static String money(TypedMoney m) {
        if (m == null || m.getCentAmount() == null) {
            return null;
        }
        int digits = m.getFractionDigits() == null ? 2 : m.getFractionDigits();
        return BigDecimal.valueOf(m.getCentAmount(), digits).toPlainString() + " " + m.getCurrencyCode();
    }

    /** "jane.doe@example.com" becomes "j***@example.com". */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    private static String initial(String name) {
        return name == null || name.isBlank() ? null : name.strip().charAt(0) + ".";
    }

    private static String shipTo(Address a) {
        if (a == null) {
            return null;
        }
        return a.getCity() == null ? a.getCountry() : a.getCity() + ", " + a.getCountry();
    }

    private static Object simplify(Object value, String locale) {
        if (value instanceof LocalizedString ls) {
            return pick(ls, locale);
        }
        if (value instanceof AttributePlainEnumValue e) {
            return e.getKey();
        }
        if (value instanceof AttributeLocalizedEnumValue e) {
            return e.getKey();
        }
        if (value instanceof TypedMoney m) {
            return money(m);
        }
        if (value instanceof Reference r) {
            return r.getTypeId().getJsonName() + ":" + r.getId();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> simplify(v, locale)).toList();
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    private static Boolean onStock(ProductVariantAvailability availability) {
        return availability == null ? null : availability.getIsOnStock();
    }

    private static String enumName(JsonEnum value) {
        return value == null ? null : value.getJsonName();
    }

    private static String refId(Reference ref) {
        return ref == null ? null : ref.getId();
    }

    private static List<String> ids(List<? extends Reference> refs) {
        return refs == null ? null : refs.stream().map(Reference::getId).toList();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private static <T, R> List<R> map(List<T> list, Function<T, R> mapper, int max) {
        return list == null ? null : list.stream().limit(max).map(mapper).toList();
    }
}
