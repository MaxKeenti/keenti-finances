package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.PlanCadence;
import com.keenti.finances.domain.model.SavingGoalPeriod;
import com.keenti.finances.domain.model.SavingGoalRevision;
import com.keenti.finances.domain.port.out.SavingGoalRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

@ApplicationScoped
public class PanacheSavingGoalRepository implements SavingGoalRepository {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public void saveRevisionTerms(SavingGoalRevision revision) {
        SavingGoalRevisionEntity entity = new SavingGoalRevisionEntity();
        entity.revision = em.getReference(BoxPlanRevisionEntity.class, revision.id());
        entity.targetAmount = revision.targetAmount();
        entity.targetDate = revision.targetDate();
        entity.regularCommitment = revision.regularCommitment();
        entity.persist();
        em.flush();
    }

    @Override
    public List<SavingGoalRevision> findRevisions(Long planId, boolean includeSuperseded) {
        String superseded = includeSuperseded
            ? ""
            : " AND revision.supersededAt IS NULL";
        return SavingGoalRevisionEntity.<SavingGoalRevisionEntity>find(
                "revision.plan.id = ?1 AND revision.plan.box.user.id = ?2"
                    + superseded
                    + " ORDER BY revision.effectiveFrom, revision.createdAt, revision.id",
                planId, userContext.getUserId())
            .stream().map(this::toDomain).toList();
    }

    @Override
    public void savePeriodMetrics(SavingGoalPeriod period) {
        SavingGoalPeriodEntity entity = new SavingGoalPeriodEntity();
        entity.period = em.getReference(BoxPlanPeriodEntity.class, period.id());
        copy(period, entity);
        entity.persist();
        em.flush();
    }

    @Override
    public void updatePeriodMetrics(SavingGoalPeriod period) {
        SavingGoalPeriodEntity entity = SavingGoalPeriodEntity.<SavingGoalPeriodEntity>find(
                "period.id = ?1 AND period.plan.id = ?2 "
                    + "AND period.plan.box.user.id = ?3",
                period.id(), period.planId(), userContext.getUserId())
            .firstResult();
        copy(period, entity);
        em.flush();
    }

    @Override
    public List<SavingGoalPeriod> findPeriods(Long planId) {
        return SavingGoalPeriodEntity.<SavingGoalPeriodEntity>find(
                "period.plan.id = ?1 AND period.plan.box.user.id = ?2 "
                    + "ORDER BY period.periodStart, period.id",
                planId, userContext.getUserId())
            .stream().map(this::toDomain).toList();
    }

    private void copy(SavingGoalPeriod source, SavingGoalPeriodEntity target) {
        target.regularCommitment = source.regularCommitment();
        target.openingArrears = source.openingArrears();
        target.requiredAmount = source.requiredAmount();
        target.arrearsCovered = source.arrearsCovered();
        target.regularProgress = source.regularProgress();
        target.extraProgress = source.extraProgress();
        target.shortfall = source.shortfall();
        target.status = source.status().name();
    }

    private SavingGoalRevision toDomain(SavingGoalRevisionEntity entity) {
        BoxPlanRevisionEntity revision = entity.revision;
        return new SavingGoalRevision(
            revision.id,
            revision.plan.id,
            revision.effectiveFrom,
            PlanCadence.valueOf(revision.cadence),
            revision.anchorWeekday,
            revision.anchorDayOfMonth,
            entity.targetAmount,
            entity.targetDate,
            entity.regularCommitment,
            revision.createdAt,
            revision.supersededAt
        );
    }

    private SavingGoalPeriod toDomain(SavingGoalPeriodEntity entity) {
        BoxPlanPeriodEntity period = entity.period;
        return new SavingGoalPeriod(
            period.id,
            period.plan.id,
            period.revision.id,
            period.periodStart,
            period.periodEndExclusive,
            period.openingBalance,
            period.closingBalance,
            period.netProgress,
            entity.regularCommitment,
            entity.openingArrears,
            entity.requiredAmount,
            entity.arrearsCovered,
            entity.regularProgress,
            entity.extraProgress,
            entity.shortfall,
            SavingGoalPeriod.Status.valueOf(entity.status),
            period.evaluatedAt
        );
    }
}
