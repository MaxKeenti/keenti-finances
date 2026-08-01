package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundingTriggerResponse(
    Long id,
    Long boxId,
    String boxName,
    Long categoryId,
    String categoryName,
    String strategy,
    BigDecimal fixedAmount,
    BigDecimal percentage,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
