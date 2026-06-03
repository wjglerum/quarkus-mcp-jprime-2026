package nl.lunatech.jprime.chat.web;

import io.quarkus.oidc.AuthenticationContext;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Step-up entry point, opened by the chat in a popup window. Requiring the silver
 * authentication context makes Quarkus OIDC re-challenge the user (when the current session
 * is only password-backed), so Keycloak runs the MFA flow and returns a token with
 * acr=urn:mace:incommon:iap:silver. The OIDC callback rewrites the session cookie for the
 * whole origin, so the main chat page is upgraded to silver without ever navigating.
 *
 * <p>Reaching this method proves the silver context is satisfied. The returned page signals
 * the opener via {@code postMessage} and closes itself, leaving the chat transcript intact.
 */
@Path("/step-up")
@Authenticated
public class StepUpResource {

    @CheckedTemplate
    static class Templates {
        public static native TemplateInstance complete();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @AuthenticationContext("urn:mace:incommon:iap:silver")
    public TemplateInstance stepUp() {
        return Templates.complete();
    }
}
