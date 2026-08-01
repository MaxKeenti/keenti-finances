package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record FundingSuggestionSet(
    Long categoryId,
    BigDecimal ingressAmount,
    List<FundingSuggestion> suggestions,
    BigDecimal combinedTotal
) {
    public FundingSuggestionSet {
        suggestions = List.copyOf(suggestions);
    }
}
