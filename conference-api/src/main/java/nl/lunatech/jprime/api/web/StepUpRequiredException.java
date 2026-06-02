package nl.lunatech.jprime.api.web;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.lunatech.jprime.api.dto.ApiError;

public class StepUpRequiredException extends WebApplicationException {

    private static final String SILVER = "urn:mace:incommon:iap:silver";

    public StepUpRequiredException(String message) {
        super(Response.status(401)
                .header("WWW-Authenticate",
                        "Bearer error=\"insufficient_user_authentication\", "
                                + "error_description=\"" + message + "\", "
                                + "acr_values=\"" + SILVER + "\"")
                .entity(ApiError.stepUp(message, SILVER))
                .type(MediaType.APPLICATION_JSON)
                .build());
    }
}
