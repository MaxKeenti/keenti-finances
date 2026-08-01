package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.SpendingBudgetPeriod;
import com.keenti.finances.domain.model.SpendingBudgetRevision;
import java.util.List;
import java.util.Optional;

public interface SpendingBudgetRepository {
    SpendingBudgetRevision saveRevision(SpendingBudgetRevision revision);
    Optional<SpendingBudgetRevision> findRevision(Long planId, Long revisionId);
    List<SpendingBudgetRevision> findRevisions(Long planId, boolean includeSuperseded);
    SpendingBudgetPeriod savePeriod(SpendingBudgetPeriod period);
    SpendingBudgetPeriod updatePeriod(SpendingBudgetPeriod period);
    List<SpendingBudgetPeriod> findPeriods(Long planId);
}
