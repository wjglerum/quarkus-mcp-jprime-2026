package nl.lunatech.jprime.api.web;

import io.quarkus.runtime.LaunchMode;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.api.seed.DemoDataSeeder;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "admin", description = "Demo operator endpoints (dev/test only)")
public class AdminResource {

    @Inject
    DemoDataSeeder demoSeeder;

    @POST
    @Path("/reseed-demo")
    public Map<String, Object> reseedDemo() {
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            throw new ForbiddenException("admin endpoints are disabled in production launch mode");
        }
        demoSeeder.wipeUserData();
        int seeded = demoSeeder.seedIfEmpty();
        return Map.of("status", "ok", "attendees_seeded", seeded);
    }
}
