package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserPreferencesRequest(
    @Min(0) @Max(359) int primaryHue,
    @NotBlank @Pattern(regexp = FontPresets.HEADING_REGEX,
        message = "headingFont must be one of the allowed presets")
    String headingFont,
    @NotBlank @Pattern(regexp = FontPresets.BODY_REGEX,
        message = "bodyFont must be one of the allowed presets")
    String bodyFont
) {}
