package nl.lunatech.jprime.mcp.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.api.PublicConferenceApi;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
public class ConferenceApiHealthCheck implements HealthCheck {

    @Inject
    @RestClient
    PublicConferenceApi api;

    @Override
    public HealthCheckResponse call() {
        try {
            int sessions = api.listSessions(null, null, null).size();
            return HealthCheckResponse.named("conference-api")
                    .up()
                    .withData("sessions", String.valueOf(sessions))
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("conference-api")
                    .down()
                    .withData("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
