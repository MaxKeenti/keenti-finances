package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.BoxPlanPeriod;
import com.keenti.finances.domain.model.BoxPlanRevision;
import com.keenti.finances.domain.model.PlanCadence;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheBoxPlanRepository implements BoxPlanRepository {

    private static final String ACTIVE_STATUSES =
        "('ACTIVE', 'READY_TO_COMPLETE', 'OVERDUE')";

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public List<BoxPlan> findAllByBoxId(Long boxId) {
        return BoxPlanEntity.<BoxPlanEntity>find(
                "box.id = ?1 AND box.user.id = ?2 ORDER BY createdAt DESC, id DESC",
                boxId, userContext.getUserId())
            .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<BoxPlan> findById(Long boxId, Long planId) {
        return BoxPlanEntity.<BoxPlanEntity>find(
                "id = ?1 AND box.id = ?2 AND box.user.id = ?3",
                planId, boxId, userContext.getUserId())
            .firstResultOptional().map(this::toDomain);
    }

    @Override
    public Optional<BoxPlan> findActiveByBoxId(Long boxId) {
        return BoxPlanEntity.<BoxPlanEntity>find(
                "box.id = ?1 AND box.user.id = ?2 AND status IN " + ACTIVE_STATUSES,
                boxId, userContext.getUserId())
            .firstResultOptional().map(this::toDomain);
    }

    @Override
    public Optional<BoxPlan> lockActiveByBoxId(Long boxId) {
        return lockPlan(boxId, null, true).map(this::toDomain);
    }

    @Override
    public Optional<BoxPlan> lockById(Long boxId, Long planId) {
        return lockPlan(boxId, planId, false).map(this::toDomain);
    }

    @Override
    public BoxPlan save(BoxPlan plan) {
        BoxPlanEntity entity = new BoxPlanEntity();
        entity.box = em.getReference(BoxEntity.class, plan.boxId());
        entity.planType = plan.type().name();
        entity.status = plan.status().name();
        entity.startDate = plan.startDate();
        entity.createdAt = plan.createdAt();
        entity.updatedAt = plan.updatedAt();
        entity.closedAt = plan.closedAt();
        entity.completionAmount = plan.completionAmount();
        entity.persist();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public BoxPlan updateStatus(Long planId, BoxPlan.Status status,
                                LocalDateTime closedAt, BigDecimal completionAmount) {
        BoxPlanEntity entity = ownedPlan(planId);
        entity.status = status.name();
        entity.closedAt = closedAt;
        entity.completionAmount = completionAmount;
        entity.updatedAt = LocalDateTime.now();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public void touch(Long planId, LocalDateTime updatedAt) {
        BoxPlanEntity entity = ownedPlan(planId);
        entity.updatedAt = updatedAt;
    }

    @Override
    public List<BoxPlanRevision> findRevisions(Long planId, boolean includeSuperseded) {
        String superseded = includeSuperseded ? "" : " AND supersededAt IS NULL";
        return BoxPlanRevisionEntity.<BoxPlanRevisionEntity>find(
                "plan.id = ?1 AND plan.box.user.id = ?2" + superseded
                    + " ORDER BY effectiveFrom, createdAt, id",
                planId, userContext.getUserId())
            .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<BoxPlanRevision> findRevisionById(Long planId, Long revisionId) {
        return BoxPlanRevisionEntity.<BoxPlanRevisionEntity>find(
                "id = ?1 AND plan.id = ?2 AND plan.box.user.id = ?3",
                revisionId, planId, userContext.getUserId())
            .firstResultOptional().map(this::toDomain);
    }

    @Override
    public BoxPlanRevision saveRevision(BoxPlanRevision revision) {
        BoxPlanRevisionEntity entity = new BoxPlanRevisionEntity();
        entity.plan = em.getReference(BoxPlanEntity.class, revision.planId());
        entity.effectiveFrom = revision.effectiveFrom();
        entity.cadence = revision.cadence().name();
        entity.anchorWeekday = revision.anchorWeekday();
        entity.anchorDayOfMonth = revision.anchorDayOfMonth();
        entity.createdAt = revision.createdAt();
        entity.supersededAt = revision.supersededAt();
        entity.persist();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public void supersedeUnopenedRevisions(Long planId, LocalDate effectiveFrom,
                                           LocalDateTime supersededAt) {
        List<BoxPlanRevisionEntity> unopened = BoxPlanRevisionEntity
            .<BoxPlanRevisionEntity>find(
                "plan.id = ?1 AND plan.box.user.id = ?2 "
                    + "AND effectiveFrom >= ?3 AND supersededAt IS NULL",
                planId, userContext.getUserId(), effectiveFrom)
            .list();
        unopened.forEach(revision -> revision.supersededAt = supersededAt);
        em.flush();
    }

    @Override
    public List<BoxPlanPeriod> findPeriods(Long planId) {
        return BoxPlanPeriodEntity.<BoxPlanPeriodEntity>find(
                "plan.id = ?1 AND plan.box.user.id = ?2 ORDER BY periodStart, id",
                planId, userContext.getUserId())
            .stream().map(this::toDomain).toList();
    }

    @Override
    public BoxPlanPeriod savePeriod(BoxPlanPeriod period) {
        BoxPlanPeriodEntity entity = new BoxPlanPeriodEntity();
        copy(period, entity);
        entity.persist();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public BoxPlanPeriod updatePeriod(BoxPlanPeriod period) {
        BoxPlanPeriodEntity entity = BoxPlanPeriodEntity.<BoxPlanPeriodEntity>find(
                "id = ?1 AND plan.id = ?2 AND plan.box.user.id = ?3",
                period.id(), period.planId(), userContext.getUserId())
            .firstResult();
        copy(period, entity);
        em.flush();
        return toDomain(entity);
    }

    private Optional<BoxPlanEntity> lockPlan(Long boxId, Long planId, boolean activeOnly) {
        StringBuilder jpql = new StringBuilder(
            "SELECT plan FROM BoxPlanEntity plan WHERE plan.box.id = :boxId "
                + "AND plan.box.user.id = :userId");
        if (planId != null) {
            jpql.append(" AND plan.id = :planId");
        }
        if (activeOnly) {
            jpql.append(" AND plan.status IN ").append(ACTIVE_STATUSES);
        }
        var query = em.createQuery(jpql.toString(), BoxPlanEntity.class)
            .setParameter("boxId", boxId)
            .setParameter("userId", userContext.getUserId())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE);
        if (planId != null) {
            query.setParameter("planId", planId);
        }
        return query.getResultStream().findFirst();
    }

    private BoxPlanEntity ownedPlan(Long planId) {
        BoxPlanEntity entity = BoxPlanEntity.<BoxPlanEntity>find(
                "id = ?1 AND box.user.id = ?2", planId, userContext.getUserId())
            .firstResult();
        if (entity == null) {
            throw new IllegalStateException("Caller-scoped Box Plan disappeared");
        }
        return entity;
    }

    private void copy(BoxPlanPeriod source, BoxPlanPeriodEntity target) {
        target.plan = em.getReference(BoxPlanEntity.class, source.planId());
        target.revision = em.getReference(BoxPlanRevisionEntity.class, source.revisionId());
        target.periodStart = source.periodStart();
        target.periodEndExclusive = source.periodEndExclusive();
        target.openingBalance = source.openingBalance();
        target.closingBalance = source.closingBalance();
        target.netProgress = source.netProgress();
        target.evaluatedAt = source.evaluatedAt();
    }

    private BoxPlan toDomain(BoxPlanEntity entity) {
        return new BoxPlan(
            entity.id,
            entity.box.id,
            BoxPlan.Type.valueOf(entity.planType),
            BoxPlan.Status.valueOf(entity.status),
            entity.startDate,
            entity.createdAt,
            entity.updatedAt,
            entity.closedAt,
            entity.completionAmount
        );
    }

    private BoxPlanRevision toDomain(BoxPlanRevisionEntity entity) {
        return new BoxPlanRevision(
            entity.id,
            entity.plan.id,
            entity.effectiveFrom,
            PlanCadence.valueOf(entity.cadence),
            entity.anchorWeekday,
            entity.anchorDayOfMonth,
            entity.createdAt,
            entity.supersededAt
        );
    }

    private BoxPlanPeriod toDomain(BoxPlanPeriodEntity entity) {
        return new BoxPlanPeriod(
            entity.id,
            entity.plan.id,
            entity.revision.id,
            entity.periodStart,
            entity.periodEndExclusive,
            entity.openingBalance,
            entity.closingBalance,
            entity.netProgress,
            entity.evaluatedAt
        );
    }
}
