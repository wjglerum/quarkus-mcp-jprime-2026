package nl.lunatech.jprime.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Uniform JSON error body returned by the API instead of hand-built strings. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String description, @JsonProperty("required_acr") String requiredAcr) {

    public static ApiError of(String error, String description) {
        return new ApiError(error, description, null);
    }
}
