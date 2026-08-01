package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.SpendingBudgetRevisionPreview;
import com.keenti.finances.domain.model.SpendingBudgetSnapshot;
import com.keenti.finances.domain.model.SpendingBudgetTerms;
import java.math.BigDecimal;
import java.util.Optional;

public interface SpendingBudgetUseCase {
    SpendingBudgetSnapshot create(Long boxId, SpendingBudgetTerms terms);
    SpendingBudgetSnapshot getActive(Long boxId);
    SpendingBudgetSnapshot get(Long boxId, Long planId);
    SpendingBudgetRevisionPreview previewRevision(Long boxId, Long planId,
                                                  SpendingBudgetTerms changes);
    SpendingBudgetSnapshot applyRevision(Long boxId, Long planId,
                                         SpendingBudgetTerms changes);
    SpendingBudgetSnapshot end(Long boxId, Long planId);
    Optional<BigDecimal> suggestedTopUp(Long boxId);
}
