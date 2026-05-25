package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String type,
    @Min(0) @Max(359) int hue
) {}
