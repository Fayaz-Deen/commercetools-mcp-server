package io.github.fayazdeen.ctmcp.config;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.defaultconfig.ApiRootBuilder;
import io.github.fayazdeen.ctmcp.tools.CartWriteTools;
import io.github.fayazdeen.ctmcp.tools.CatalogTools;
import io.github.fayazdeen.ctmcp.tools.CustomerTools;
import io.github.fayazdeen.ctmcp.tools.OrderTools;
import io.vrap.rmf.base.client.oauth2.ClientCredentials;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the commercetools client and registers the MCP tools. */
@Configuration(proxyBeanMethods = false)
public class McpServerConfig {

    /** Property that unlocks write tools; mapped from CT_MCP_ALLOW_WRITES. */
    public static final String ALLOW_WRITES = "ct.mcp.allow-writes";

    @Bean(destroyMethod = "close")
    ProjectApiRoot projectApiRoot(CtpProperties ctp) {
        ClientCredentials credentials = ClientCredentials.of()
                .withClientId(ctp.clientId())
                .withClientSecret(ctp.clientSecret())
                .withScopes(ctp.scopes())
                .build();
        return ApiRootBuilder.of()
                .defaultClient(credentials, ctp.tokenUrl(), ctp.apiUrl())
                .build(ctp.projectKey());
    }

    @Bean
    ToolCallbackProvider readOnlyTools(ProjectApiRoot api) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new CatalogTools(api), new OrderTools(api), new CustomerTools(api))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = ALLOW_WRITES, havingValue = "true")
    ToolCallbackProvider writeTools(ProjectApiRoot api) {
        return MethodToolCallbackProvider.builder().toolObjects(new CartWriteTools(api)).build();
    }
}
