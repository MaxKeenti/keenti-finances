package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundingTrigger(
    Long id,
    Long boxId,
    String boxName,
    Long categoryId,
    String categoryName,
    Strategy strategy,
    BigDecimal fixedAmount,
    BigDecimal percentage,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public enum Strategy {
        PLAN_DERIVED,
        FIXED_AMOUNT,
        PERCENTAGE
    }
}
