package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 50) String phone,
    @Size(max = 255) String email
) {}
