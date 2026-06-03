package nl.lunatech.jprime.chat.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.chat.security.CurrentUser;

/**
 * Returns the caller's current identity as JSON. The chat fetches this after a step-up popup
 * completes to refresh the acr/amr shown in the sidebar, confirming the session was upgraded
 * to silver without reloading the page (which would lose the transcript).
 */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class IdentityResource {

    @Inject
    CurrentUser me;

    @GET
    public Me me() {
        return new Me(me.subject(), me.name(), me.rolesDisplay(), me.acr(), me.amrDisplay());
    }

    public record Me(String subject, String name, String roles, String acr, String amr) {}
}
