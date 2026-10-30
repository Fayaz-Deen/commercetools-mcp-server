package io.github.fayazdeen.ctmcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

class McpServerConfigTest {

    private static final List<String> READ_TOOLS = List.of("search_products", "get_product", "get_price",
            "list_categories", "get_inventory", "get_cart", "get_order", "list_recent_orders",
            "list_discount_codes", "get_customer");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Props.class, McpServerConfig.class)
            .withPropertyValues(
                    "ctp.project-key=demo",
                    "ctp.client-id=placeholder-id",
                    "ctp.client-secret=placeholder-secret",
                    "ctp.auth-url=https://auth.example.test",
                    "ctp.api-url=https://api.example.test");

    @Test
    void registersOnlyReadOnlyToolsByDefault() {
        runner.run(context -> assertThat(toolNames(context))
                .containsExactlyInAnyOrderElementsOf(READ_TOOLS)
                .doesNotContain("add_line_item_to_cart"));
    }

    @Test
    void writeToolsStayOffForAnyValueOtherThanTrue() {
        runner.withPropertyValues(McpServerConfig.ALLOW_WRITES + "=yes")
                .run(context -> assertThat(toolNames(context)).doesNotContain("add_line_item_to_cart"));
    }

    @Test
    void registersWriteToolWhenExplicitlyAllowed() {
        runner.withPropertyValues(McpServerConfig.ALLOW_WRITES + "=true")
                .run(context -> assertThat(toolNames(context))
                        .contains("add_line_item_to_cart")
                        .containsAll(READ_TOOLS));
    }

    @Test
    void everyToolHasADescriptionAndInputSchema() {
        runner.withPropertyValues(McpServerConfig.ALLOW_WRITES + "=true").run(context -> context
                .getBeansOfType(ToolCallbackProvider.class).values().stream()
                .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
                .forEach(tool -> {
                    assertThat(tool.getToolDefinition().description()).isNotBlank();
                    assertThat(tool.getToolDefinition().inputSchema()).contains("\"type\"");
                }));
    }

    @Test
    void failsFastWhenCredentialsAreMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(Props.class, McpServerConfig.class)
                .withPropertyValues("ctp.project-key=demo")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("CTP_CLIENT_ID, CTP_CLIENT_SECRET, CTP_AUTH_URL, CTP_API_URL"));
    }

    private static List<String> toolNames(ApplicationContext context) {
        return context.getBeansOfType(ToolCallbackProvider.class).values().stream()
                .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
                .map(tool -> tool.getToolDefinition().name())
                .toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CtpProperties.class)
    static class Props {
    }
}
