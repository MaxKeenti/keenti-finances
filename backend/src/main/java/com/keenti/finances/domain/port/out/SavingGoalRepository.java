package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.SavingGoalPeriod;
import com.keenti.finances.domain.model.SavingGoalRevision;
import java.util.List;

public interface SavingGoalRepository {
    void saveRevisionTerms(SavingGoalRevision revision);
    List<SavingGoalRevision> findRevisions(Long planId, boolean includeSuperseded);
    void savePeriodMetrics(SavingGoalPeriod period);
    void updatePeriodMetrics(SavingGoalPeriod period);
    List<SavingGoalPeriod> findPeriods(Long planId);
}
