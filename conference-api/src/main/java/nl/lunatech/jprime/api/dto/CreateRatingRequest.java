package nl.lunatech.jprime.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateRatingRequest(@Min(1) @Max(5) int stars, String comment) {}
