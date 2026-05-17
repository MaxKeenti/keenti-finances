package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubscriptionResponse(
    Long id,
    String name,
    BigDecimal cost,
    String billingCycle,
    String type,
    Long categoryId,
    LocalDate nextBillingDate,
    String tokenUuid,
    LocalDateTime createdAt,
    boolean ownerParticipates
) {}
