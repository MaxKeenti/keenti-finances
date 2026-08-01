package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;

public record FundingSuggestionSetResponse(
    Long categoryId,
    BigDecimal ingressAmount,
    List<FundingSuggestionResponse> suggestions,
    BigDecimal combinedTotal
) {}
