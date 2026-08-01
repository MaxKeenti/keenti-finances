package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;

public record FundingSuggestionResponse(
    Long triggerId,
    Long boxId,
    String boxName,
    String strategy,
    BigDecimal suggestedAmount
) {}
