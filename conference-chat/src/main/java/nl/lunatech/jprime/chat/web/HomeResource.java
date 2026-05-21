package nl.lunatech.jprime.chat.web;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;

/**
 * Serving the chat SPA at "/" makes the OIDC web-app flow kick in
 * automatically: hitting "/" without a session redirects to Keycloak.
 */
@Path("/")
@Authenticated
public class HomeResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index() {
        InputStream html = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/chat.html");
        if (html == null) {
            return Response.serverError().entity("chat.html missing").build();
        }
        return Response.ok(html).type(MediaType.TEXT_HTML).build();
    }
}
