package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.01") BigDecimal cost,
    /** Defaults to the Primary Currency on create and to the Subscription's own on update. */
    @Size(max = 3) String currency,
    @NotBlank String billingCycle,
    @NotBlank String type,
    Long categoryId,
    @NotNull LocalDate nextBillingDate,
    Boolean ownerParticipates
) {
    public SubscriptionRequest(String name, BigDecimal cost, String billingCycle, String type,
                               Long categoryId, LocalDate nextBillingDate, Boolean ownerParticipates) {
        this(name, cost, null, billingCycle, type, categoryId, nextBillingDate, ownerParticipates);
    }

    public boolean ownerParticipatesOrDefault() {
        return ownerParticipates == null || ownerParticipates;
    }
}
