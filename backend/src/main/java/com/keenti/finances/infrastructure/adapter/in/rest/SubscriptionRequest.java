package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.01") BigDecimal cost,
    @NotBlank String billingCycle,
    @NotBlank String type,
    Long categoryId,
    @NotNull LocalDate nextBillingDate
) {}
