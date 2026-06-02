package nl.lunatech.jprime.chat.web;

import io.quarkus.oidc.AuthenticationContext;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

/**
 * Step-up entry point. Requiring the silver authentication context makes Quarkus OIDC
 * re-challenge the user (when the current session is only password-backed), so Keycloak
 * runs the MFA flow and returns a token with acr=urn:mace:incommon:iap:silver. Once the
 * session satisfies it, we bounce back to the chat where the sensitive action can be retried.
 */
@Path("/step-up")
@Authenticated
public class StepUpResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    @AuthenticationContext("urn:mace:incommon:iap:silver")
    public Response stepUp() {
        return Response.seeOther(URI.create("/")).build();
    }
}
