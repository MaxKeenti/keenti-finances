package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BoxRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Min(0) @Max(359) Integer hue,
    @Size(max = 16) String icon,
    @Size(max = 500) String description
) {}
