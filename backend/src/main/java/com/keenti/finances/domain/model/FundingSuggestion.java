package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public record FundingSuggestion(
    Long triggerId,
    Long boxId,
    String boxName,
    FundingTrigger.Strategy strategy,
    BigDecimal suggestedAmount
) {}
