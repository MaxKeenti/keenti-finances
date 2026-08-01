package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.port.in.SavingGoalUseCase;
import com.keenti.finances.domain.port.in.SpendingBudgetUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxPlanSuggestionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Optional;

@ApplicationScoped
public class BoxPlanSuggestionService implements BoxPlanSuggestionPort {

    @Inject
    BoxPlanRepository boxPlanRepository;

    @Inject
    SavingGoalUseCase savingGoalUseCase;

    @Inject
    SpendingBudgetUseCase spendingBudgetUseCase;

    @Override
    @Transactional
    public Optional<BigDecimal> suggestedContribution(Long boxId) {
        return boxPlanRepository.findActiveByBoxId(boxId)
            .flatMap(plan -> switch (plan.type()) {
                case SAVING_GOAL -> savingGoalUseCase.getActive(boxId)
                    .map(goal -> goal.currentCommitment()
                        .min(goal.remainingAmount()));
                case SPENDING_BUDGET -> spendingBudgetUseCase.suggestedTopUp(boxId);
            });
    }
}
