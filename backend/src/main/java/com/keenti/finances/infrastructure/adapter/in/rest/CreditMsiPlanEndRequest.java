package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

public record CreditMsiPlanEndRequest(@NotBlank String reason) {}
