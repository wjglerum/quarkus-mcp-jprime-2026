package nl.lunatech.jprime.chat.web;

import io.quarkus.oidc.AuthenticationContext;
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
 * <p>Reaching this method proves the silver context is satisfied. Instead of redirecting
 * (which only made sense for the old full-page flow), it returns a tiny page that signals
 * the opener via {@code postMessage} and closes itself, leaving the chat transcript intact.
 */
@Path("/step-up")
@Authenticated
public class StepUpResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    @AuthenticationContext("urn:mace:incommon:iap:silver")
    public String stepUp() {
        return COMPLETE_PAGE;
    }

    // Self-contained: the opener and this popup are same-origin, so the message reaches the
    // chat. window.close() works because the chat opened this window. If a browser refuses to
    // close a script-opened window, the visible line tells the user they can close it.
    private static final String COMPLETE_PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>MFA complete</title>
              <style>
                body { margin: 0; height: 100vh; display: grid; place-items: center;
                       font-family: system-ui, sans-serif; background: #11161c; color: #f2a65a; }
                p { font-size: 15px; }
              </style>
            </head>
            <body>
              <p>MFA complete. You can close this window.</p>
              <script>
                (function () {
                  try {
                    if (window.opener && !window.opener.closed) {
                      window.opener.postMessage({ type: 'step-up-complete' }, window.location.origin);
                    }
                  } catch (e) { /* opener gone or cross-origin: fall through to close */ }
                  window.close();
                })();
              </script>
            </body>
            </html>
            """;
}
