package com.keenti.finances.domain.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/** Resolves the current suggestion for whichever active plan a Box owns. */
public interface BoxPlanSuggestionPort {
    Optional<BigDecimal> suggestedContribution(Long boxId);
}
