package nl.lunatech.jprime.api.web;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class StepUpRequiredException extends WebApplicationException {

    public StepUpRequiredException(String message) {
        super(Response.status(401)
                .header("WWW-Authenticate",
                        "Bearer error=\"insufficient_user_authentication\", "
                                + "error_description=\"" + message + "\", "
                                + "acr_values=\"urn:mace:incommon:iap:silver\"")
                .entity("{\"error\":\"insufficient_user_authentication\",\"description\":\""
                        + message + "\",\"required_acr\":\"urn:mace:incommon:iap:silver\"}")
                .type("application/json")
                .build());
    }
}
