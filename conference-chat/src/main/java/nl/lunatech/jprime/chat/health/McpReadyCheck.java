package nl.lunatech.jprime.chat.health;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class McpReadyCheck implements HealthCheck {

    @Inject
    ToolProvider mcpToolProvider;

    @Override
    public HealthCheckResponse call() {
        var b = HealthCheckResponse.named("mcp-conference");
        try {
            ToolProviderResult tools = mcpToolProvider.provideTools(null);
            int count = tools == null ? 0 : tools.aiServiceTools().size();
            b.withData("tool-count", String.valueOf(count));
            return count > 0 ? b.up().build() : b.withData("reason", "no tools listed").down().build();
        } catch (Exception e) {
            return b.withData("error", e.getClass().getSimpleName())
                    .withData("message", e.getMessage() == null ? "" : e.getMessage())
                    .down()
                    .build();
        }
    }
}
