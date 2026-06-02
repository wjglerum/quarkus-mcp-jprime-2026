package nl.lunatech.jprime.chat.security;

import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.OidcRedirectFilter;
import io.quarkus.oidc.Redirect;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Adds {@code acr_values} to the OIDC authentication redirect when the user is stepping up
 * (i.e. the request that triggered re-authentication targets {@code /step-up}). Without this,
 * Quarkus' {@code @AuthenticationContext} re-login for a web-app does not carry the required
 * acr to Keycloak, so the provider returns the existing low-assurance session and the browser
 * loops. Normal logins (any other path) are left untouched so they stay password-only.
 */
@ApplicationScoped
@Unremovable
@Redirect(Redirect.Location.OIDC_AUTHORIZATION)
public class StepUpRedirectFilter implements OidcRedirectFilter {

    static final String SILVER = "urn:mace:incommon:iap:silver";

    @Override
    public void filter(OidcRedirectFilter.OidcRedirectContext context) {
        String path = context.routingContext().request().path();
        if (path != null && path.startsWith("/step-up")) {
            context.additionalQueryParams().add("acr_values", SILVER);
        }
    }
}
