package io.github.fayazdeen.ctmcp.tools;

import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.fixture;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.ok;
import static io.github.fayazdeen.ctmcp.tools.FakeCommercetools.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CustomerToolsTest {

    private static final String CUSTOMER_ID = "d4c3b2a1-6f5e-4b7a-9d8c-5c4b3a2f1e0d";

    private final FakeCommercetools ct = new FakeCommercetools();
    private final CustomerTools tools = new CustomerTools(ct.api);

    @Test
    void getCustomer_byIdRedactsSensitiveFields() throws Exception {
        ct.respond(ok(fixture("customer.json")));

        var customer = tools.getCustomer(CUSTOMER_ID, null);

        assertThat(ct.lastUrl()).isEqualTo("/demo/customers/" + CUSTOMER_ID);
        assertThat(customer.email()).isEqualTo("j***@example.com");
        assertThat(customer.firstName()).isEqualTo("Jane");
        assertThat(customer.lastName()).isEqualTo("D.");
        assertThat(customer.addressCount()).isEqualTo(2);
        assertThat(customer.addressCountries()).containsExactly("DE", "AT");
        assertThat(customer.stores()).containsExactly("eu-store");
        assertThat(customer.customerNumber()).isEqualTo("C-1001");

        String json = new ObjectMapper().writeValueAsString(customer);
        assertThat(json).doesNotContain("SYNTHETIC-PASSWORD-HASH", "password", "1990-05-17", "Doe",
                "jane.doe", "Examplestrasse", "Berlin", "+49");
    }

    @Test
    void getCustomer_byEmailUsesPredicateVariable() {
        ct.respond(ok(page(fixture("customer.json"))));

        var customer = tools.getCustomer(null, "jane.doe@example.com");

        assertThat(ct.lastUrl()).startsWith("/demo/customers?")
                .contains("where=email = :email", "var.email=jane.doe@example.com", "limit=1");
        assertThat(customer.id()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void getCustomer_unknownEmailIsNotFound() {
        ct.respond(ok(page()));

        assertThatThrownBy(() -> tools.getCustomer(null, "nobody@example.com"))
                .isInstanceOf(NoSuchElementException.class).hasMessage("customer not found");
    }

    @Test
    void getCustomer_validatesInput() {
        assertThatThrownBy(() -> tools.getCustomer(null, null)).hasMessageContaining("exactly one of: id, email");
        assertThatThrownBy(() -> tools.getCustomer(null, "jane")).hasMessageContaining("'email' must be");
        assertThatThrownBy(() -> tools.getCustomer("42", null)).hasMessageContaining("'id' must be");
        verifyNoInteractions(ct.http);
    }
}
