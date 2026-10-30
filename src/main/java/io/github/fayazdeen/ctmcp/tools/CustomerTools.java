package io.github.fayazdeen.ctmcp.tools;

import java.util.NoSuchElementException;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.customer.Customer;
import io.github.fayazdeen.ctmcp.tools.Views.CustomerView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Read-only customer lookup. Results are redacted: no password hash, phone, birth date or addresses. */
public class CustomerTools {

    private final ProjectApiRoot api;

    public CustomerTools(ProjectApiRoot api) {
        this.api = api;
    }

    @Tool(name = "get_customer", description = "Look up a customer by id or email. Sensitive fields are"
            + " redacted: email is masked, last name reduced to an initial, addresses reduced to countries.")
    public CustomerView getCustomer(
            @ToolParam(description = "Customer id (UUID)", required = false) String id,
            @ToolParam(description = "Customer email", required = false) String email) {
        Inputs.exactlyOne("id, email", id, email);
        Customer customer;
        if (Inputs.present(id)) {
            String customerId = Inputs.id("id", id);
            customer = CtCalls.call("customer " + customerId, "view_customers",
                    () -> api.customers().withId(customerId).get().executeBlocking().getBody());
        } else {
            String address = Inputs.email("email", email);
            customer = CtCalls.call("customer", "view_customers",
                    () -> api.customers().get().withWhere("email = :email").withPredicateVar("email", address)
                            .withLimit(1).executeBlocking().getBody())
                    .getResults().stream().findFirst()
                    .orElseThrow(() -> new NoSuchElementException("customer not found"));
        }
        return Views.customer(customer);
    }
}
