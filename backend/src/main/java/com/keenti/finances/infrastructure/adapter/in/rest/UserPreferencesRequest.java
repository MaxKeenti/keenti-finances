package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserPreferencesRequest(
    @Min(0) @Max(359) int primaryHue,
    @NotBlank @Pattern(regexp = "^(Fraunces|Playfair Display)$",
        message = "headingFont must be one of: Fraunces, Playfair Display")
    String headingFont,
    @NotBlank @Pattern(regexp = "^(Geist|Inter|System UI)$",
        message = "bodyFont must be one of: Geist, Inter, System UI")
    String bodyFont
) {}
