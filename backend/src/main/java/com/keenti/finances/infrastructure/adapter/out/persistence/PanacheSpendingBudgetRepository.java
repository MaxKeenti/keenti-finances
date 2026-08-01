package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.BoxPlanPeriod;
import com.keenti.finances.domain.model.BoxPlanRevision;
import com.keenti.finances.domain.model.SpendingBudgetPeriod;
import com.keenti.finances.domain.model.SpendingBudgetRevision;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.SpendingBudgetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheSpendingBudgetRepository implements SpendingBudgetRepository {

    @Inject
    BoxPlanRepository boxPlanRepository;

    @Override
    public SpendingBudgetRevision saveRevision(SpendingBudgetRevision revision) {
        SpendingBudgetRevisionEntity entity = new SpendingBudgetRevisionEntity();
        entity.revisionId = revision.planRevision().id();
        entity.desiredBalance = revision.desiredBalance();
        entity.persist();
        return revision;
    }

    @Override
    public Optional<SpendingBudgetRevision> findRevision(Long planId, Long revisionId) {
        return SpendingBudgetRevisionEntity.<SpendingBudgetRevisionEntity>findByIdOptional(revisionId)
            .flatMap(entity -> boxPlanRepository.findRevisionById(planId, revisionId)
                .map(revision -> toDomain(entity, revision)));
    }

    @Override
    public List<SpendingBudgetRevision> findRevisions(
            Long planId, boolean includeSuperseded) {
        return boxPlanRepository.findRevisions(planId, includeSuperseded).stream()
            .map(revision -> findRevision(planId, revision.id()))
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public SpendingBudgetPeriod savePeriod(SpendingBudgetPeriod period) {
        SpendingBudgetPeriodEntity entity = toEntity(period);
        entity.persist();
        return period;
    }

    @Override
    public SpendingBudgetPeriod updatePeriod(SpendingBudgetPeriod period) {
        SpendingBudgetPeriodEntity entity = SpendingBudgetPeriodEntity.findById(
            period.planPeriod().id());
        copy(period, entity);
        return period;
    }

    @Override
    public List<SpendingBudgetPeriod> findPeriods(Long planId) {
        return boxPlanRepository.findPeriods(planId).stream()
            .map(period -> SpendingBudgetPeriodEntity
                .<SpendingBudgetPeriodEntity>findByIdOptional(period.id())
                .map(entity -> toDomain(entity, period)))
            .flatMap(Optional::stream)
            .toList();
    }

    private SpendingBudgetPeriodEntity toEntity(SpendingBudgetPeriod period) {
        SpendingBudgetPeriodEntity entity = new SpendingBudgetPeriodEntity();
        entity.periodId = period.planPeriod().id();
        copy(period, entity);
        return entity;
    }

    private void copy(SpendingBudgetPeriod source, SpendingBudgetPeriodEntity target) {
        target.deposits = source.deposits();
        target.withdrawals = source.withdrawals();
        target.transfersIn = source.transfersIn();
        target.transfersOut = source.transfersOut();
        target.fundedSpending = source.fundedSpending();
        target.suggestedTopUp = source.suggestedTopUp();
    }

    private SpendingBudgetRevision toDomain(SpendingBudgetRevisionEntity entity,
                                            BoxPlanRevision revision) {
        return new SpendingBudgetRevision(revision, entity.desiredBalance);
    }

    private SpendingBudgetPeriod toDomain(SpendingBudgetPeriodEntity entity,
                                          BoxPlanPeriod period) {
        return new SpendingBudgetPeriod(
            period,
            entity.deposits,
            entity.withdrawals,
            entity.transfersIn,
            entity.transfersOut,
            entity.fundedSpending,
            entity.suggestedTopUp
        );
    }
}
