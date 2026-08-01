package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxHistoryEntry;
import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.BoxPlanPeriod;
import com.keenti.finances.domain.model.BoxPlanRevision;
import com.keenti.finances.domain.model.PlanCadence;
import com.keenti.finances.domain.model.PlanSchedule;
import com.keenti.finances.domain.model.SpendingBudgetCalculation;
import com.keenti.finances.domain.model.SpendingBudgetCalculator;
import com.keenti.finances.domain.model.SpendingBudgetPeriod;
import com.keenti.finances.domain.model.SpendingBudgetRevision;
import com.keenti.finances.domain.model.SpendingBudgetRevisionPreview;
import com.keenti.finances.domain.model.SpendingBudgetSnapshot;
import com.keenti.finances.domain.model.SpendingBudgetTerms;
import com.keenti.finances.domain.port.in.SpendingBudgetUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.SpendingBudgetRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class SpendingBudgetService implements SpendingBudgetUseCase {

    private static final int MAX_CATCH_UP_PERIODS = 2_000;

    @Inject
    BoxRepository boxRepository;

    @Inject
    BoxPlanRepository boxPlanRepository;

    @Inject
    SpendingBudgetRepository spendingBudgetRepository;

    @Inject
    UserTimeZoneProvider userTimeZoneProvider;

    @Override
    @Transactional
    public SpendingBudgetSnapshot create(Long boxId, SpendingBudgetTerms requested) {
        SpendingBudgetTerms terms = requireCompleteTerms(requested);
        Box box = boxRepository.lockActiveById(boxId)
            .orElseThrow(() -> new NotFoundException("Box not found: " + boxId));
        if (boxPlanRepository.lockActiveByBoxId(boxId).isPresent()) {
            throw conflict("Box already has an active plan");
        }

        LocalDate today = userTimeZoneProvider.today();
        LocalDateTime now = LocalDateTime.now();
        BoxPlan plan = boxPlanRepository.save(new BoxPlan(
            null,
            boxId,
            BoxPlan.Type.SPENDING_BUDGET,
            BoxPlan.Status.ACTIVE,
            today,
            now,
            now,
            null,
            null
        ));
        saveRevision(plan.id(), today, terms, now);
        return snapshot(plan, today, boxRepository.findHistory(boxId));
    }

    @Override
    @Transactional
    public SpendingBudgetSnapshot getActive(Long boxId) {
        BoxPlan plan = boxPlanRepository.findActiveByBoxId(boxId)
            .filter(candidate -> candidate.type() == BoxPlan.Type.SPENDING_BUDGET)
            .orElseThrow(() -> new NotFoundException(
                "Active Spending Budget not found for Box: " + boxId));
        return get(boxId, plan.id());
    }

    @Override
    @Transactional
    public SpendingBudgetSnapshot get(Long boxId, Long planId) {
        BoxPlan plan = requireBudget(boxId, planId, false);
        if (plan.status().isActive()) {
            plan = requireBudget(boxId, planId, true);
        }
        LocalDate today = userTimeZoneProvider.today();
        List<BoxHistoryEntry> history = boxRepository.findHistory(boxId);
        if (plan.status().isActive()) {
            evaluateClosedPeriods(plan, today, history);
            plan = requireBudget(boxId, planId, false);
        }
        return snapshot(plan, today, history);
    }

    @Override
    @Transactional
    public SpendingBudgetRevisionPreview previewRevision(
            Long boxId, Long planId, SpendingBudgetTerms changes) {
        BoxPlan plan = requireBudget(boxId, planId, true);
        LocalDate today = userTimeZoneProvider.today();
        List<BoxHistoryEntry> history = boxRepository.findHistory(boxId);
        evaluateClosedPeriods(plan, today, history);

        SpendingBudgetRevision current = currentRevision(plan.id(), today);
        SpendingBudgetTerms merged = merge(current, changes);
        PlanSchedule.DateRange currentRange = currentRange(plan, today, current.planRevision());
        BigDecimal currentBalance = boxRepository.getBalance(boxId);
        return new SpendingBudgetRevisionPreview(
            plan.id(),
            currentRange.endExclusive(),
            merged.cadence(),
            merged.anchorWeekday(),
            merged.anchorDayOfMonth(),
            merged.desiredBalance(),
            currentBalance,
            suggestedTopUp(merged.desiredBalance(), currentBalance)
        );
    }

    @Override
    @Transactional
    public SpendingBudgetSnapshot applyRevision(
            Long boxId, Long planId, SpendingBudgetTerms changes) {
        boxRepository.lockActiveById(boxId)
            .orElseThrow(() -> new NotFoundException("Box not found: " + boxId));
        BoxPlan locked = requireBudget(boxId, planId, true);
        LocalDate today = userTimeZoneProvider.today();
        List<BoxHistoryEntry> history = boxRepository.findHistory(boxId);
        evaluateClosedPeriods(locked, today, history);

        SpendingBudgetRevision current = currentRevision(planId, today);
        SpendingBudgetTerms merged = merge(current, changes);
        LocalDate effectiveFrom = currentRange(
            locked, today, current.planRevision()).endExclusive();
        LocalDateTime now = LocalDateTime.now();

        boxPlanRepository.supersedeUnopenedRevisions(planId, effectiveFrom, now);
        saveRevision(planId, effectiveFrom, merged, now);
        boxPlanRepository.touch(planId, now);
        return snapshot(requireBudget(boxId, planId, false), today, history);
    }

    @Override
    @Transactional
    public SpendingBudgetSnapshot end(Long boxId, Long planId) {
        boxRepository.lockActiveById(boxId)
            .orElseThrow(() -> new NotFoundException("Box not found: " + boxId));
        BoxPlan locked = requireBudget(boxId, planId, true);
        LocalDate today = userTimeZoneProvider.today();
        List<BoxHistoryEntry> history = boxRepository.findHistory(boxId);
        evaluateClosedPeriods(locked, today, history);
        LocalDateTime now = LocalDateTime.now();
        boxPlanRepository.supersedeUnopenedRevisions(
            planId, today.plusDays(1), now);
        BoxPlan ended = boxPlanRepository.updateStatus(
            planId, BoxPlan.Status.ENDED, now, boxRepository.getBalance(boxId));
        return snapshot(ended, today, history);
    }

    @Override
    @Transactional
    public Optional<BigDecimal> suggestedTopUp(Long boxId) {
        if (boxRepository.findActiveById(boxId).isEmpty()) {
            return Optional.empty();
        }
        return boxPlanRepository.findActiveByBoxId(boxId)
            .filter(plan -> plan.type() == BoxPlan.Type.SPENDING_BUDGET)
            .map(plan -> {
                LocalDate today = userTimeZoneProvider.today();
                SpendingBudgetRevision revision = currentRevision(plan.id(), today);
                return suggestedTopUp(
                    revision.desiredBalance(), boxRepository.getBalance(boxId));
            });
    }

    private void evaluateClosedPeriods(BoxPlan plan, LocalDate today,
                                       List<BoxHistoryEntry> history) {
        List<SpendingBudgetRevision> revisions = spendingBudgetRepository
            .findRevisions(plan.id(), false);
        if (revisions.isEmpty()) {
            throw new IllegalStateException("Spending Budget has no revision");
        }

        Map<LocalDate, SpendingBudgetPeriod> existing = new HashMap<>();
        for (SpendingBudgetPeriod period : spendingBudgetRepository.findPeriods(plan.id())) {
            existing.put(period.planPeriod().periodStart(), period);
        }

        LocalDate cursor = plan.startDate();
        int evaluated = 0;
        while (cursor.isBefore(today)) {
            if (++evaluated > MAX_CATCH_UP_PERIODS) {
                throw new IllegalStateException("Spending Budget period catch-up exceeded safety bound");
            }
            SpendingBudgetRevision revision = revisionAt(revisions, cursor);
            LocalDate endExclusive = PlanSchedule.nextBoundary(
                cursor,
                revision.planRevision().cadence(),
                revision.planRevision().anchorWeekday(),
                revision.planRevision().anchorDayOfMonth()
            );
            if (endExclusive.isAfter(today)) {
                break;
            }

            SpendingBudgetCalculation calculation = SpendingBudgetCalculator.calculate(
                history, cursor, endExclusive, revision.desiredBalance(),
                cursor.equals(plan.startDate()) ? plan.createdAt() : null);
            SpendingBudgetPeriod previous = existing.get(cursor);
            LocalDateTime evaluatedAt = LocalDateTime.now();
            BoxPlanPeriod generic = new BoxPlanPeriod(
                previous == null ? null : previous.planPeriod().id(),
                plan.id(),
                revision.planRevision().id(),
                cursor,
                endExclusive,
                calculation.openingBalance(),
                calculation.closingBalance(),
                calculation.netProgress(),
                evaluatedAt
            );
            BoxPlanPeriod savedGeneric = previous == null
                ? boxPlanRepository.savePeriod(generic)
                : boxPlanRepository.updatePeriod(generic);
            SpendingBudgetPeriod evaluatedPeriod = new SpendingBudgetPeriod(
                savedGeneric,
                calculation.deposits(),
                calculation.withdrawals(),
                calculation.transfersIn(),
                calculation.transfersOut(),
                calculation.fundedSpending(),
                calculation.suggestedTopUp()
            );
            if (previous == null) {
                spendingBudgetRepository.savePeriod(evaluatedPeriod);
            } else {
                spendingBudgetRepository.updatePeriod(evaluatedPeriod);
            }
            cursor = endExclusive;
        }
    }

    private SpendingBudgetSnapshot snapshot(BoxPlan plan, LocalDate today,
                                            List<BoxHistoryEntry> history) {
        LocalDate asOfDate = plan.status().isActive() || plan.closedAt() == null
            ? today
            : plan.closedAt().toLocalDate();
        List<BoxHistoryEntry> visibleHistory = plan.status().isActive()
            || plan.closedAt() == null
            ? history
            : history.stream()
                .filter(entry -> entry.effectiveDate().isBefore(asOfDate)
                    || (entry.effectiveDate().equals(asOfDate)
                        && !entry.createdAt().isAfter(plan.closedAt())))
                .toList();
        SpendingBudgetRevision revision = currentRevision(plan.id(), asOfDate);
        PlanSchedule.DateRange range = currentRange(
            plan, asOfDate, revision.planRevision());
        BigDecimal currentBalance = !plan.status().isActive()
                && plan.completionAmount() != null
            ? plan.completionAmount()
            : boxRepository.getBalance(plan.boxId());
        SpendingBudgetCalculation current = SpendingBudgetCalculator.calculate(
            visibleHistory, range.start(), range.endExclusive(), revision.desiredBalance(),
            range.start().equals(plan.startDate()) ? plan.createdAt() : null);
        SpendingBudgetPeriod currentPeriod = new SpendingBudgetPeriod(
            new BoxPlanPeriod(
                null,
                plan.id(),
                revision.planRevision().id(),
                range.start(),
                range.endExclusive(),
                current.openingBalance(),
                current.closingBalance(),
                current.netProgress(),
                LocalDateTime.now()
            ),
            current.deposits(),
            current.withdrawals(),
            current.transfersIn(),
            current.transfersOut(),
            current.fundedSpending(),
            current.suggestedTopUp()
        );
        return new SpendingBudgetSnapshot(
            plan,
            revision,
            spendingBudgetRepository.findRevisions(plan.id(), true),
            spendingBudgetRepository.findPeriods(plan.id()),
            currentPeriod,
            range.start(),
            range.endExclusive(),
            currentBalance,
            suggestedTopUp(revision.desiredBalance(), currentBalance)
        );
    }

    private PlanSchedule.DateRange currentRange(BoxPlan plan, LocalDate today,
                                                BoxPlanRevision revision) {
        LocalDate start = plan.startDate();
        List<BoxPlanPeriod> periods = boxPlanRepository.findPeriods(plan.id());
        if (!periods.isEmpty()) {
            start = periods.get(periods.size() - 1).periodEndExclusive();
        }
        if (today.isBefore(start)) {
            start = today;
        }
        LocalDate end = PlanSchedule.nextBoundary(
            start,
            revision.cadence(),
            revision.anchorWeekday(),
            revision.anchorDayOfMonth()
        );
        return new PlanSchedule.DateRange(start, end);
    }

    private SpendingBudgetRevision currentRevision(Long planId, LocalDate date) {
        return revisionAt(
            spendingBudgetRepository.findRevisions(planId, false), date);
    }

    private SpendingBudgetRevision revisionAt(List<SpendingBudgetRevision> revisions,
                                              LocalDate date) {
        return revisions.stream()
            .filter(revision -> !revision.planRevision().effectiveFrom().isAfter(date))
            .max(Comparator.comparing(
                revision -> revision.planRevision().effectiveFrom()))
            .orElseThrow(() -> new IllegalStateException(
                "No Spending Budget revision applies on " + date));
    }

    private SpendingBudgetRevision saveRevision(Long planId, LocalDate effectiveFrom,
                                                SpendingBudgetTerms terms,
                                                LocalDateTime createdAt) {
        BoxPlanRevision generic = boxPlanRepository.saveRevision(new BoxPlanRevision(
            null,
            planId,
            effectiveFrom,
            terms.cadence(),
            terms.anchorWeekday(),
            terms.anchorDayOfMonth(),
            createdAt,
            null
        ));
        return spendingBudgetRepository.saveRevision(new SpendingBudgetRevision(
            generic, terms.desiredBalance()));
    }

    private SpendingBudgetTerms merge(SpendingBudgetRevision current,
                                      SpendingBudgetTerms changes) {
        if (changes == null) {
            return requireCompleteTerms(new SpendingBudgetTerms(
                current.desiredBalance(),
                current.planRevision().cadence(),
                current.planRevision().anchorWeekday(),
                current.planRevision().anchorDayOfMonth()
            ));
        }
        PlanCadence cadence = changes.cadence() == null
            ? current.planRevision().cadence()
            : changes.cadence();
        Integer weekday = cadence == PlanCadence.WEEKLY
            ? changes.anchorWeekday() != null
                ? changes.anchorWeekday()
                : current.planRevision().cadence() == PlanCadence.WEEKLY
                    ? current.planRevision().anchorWeekday()
                    : null
            : null;
        Integer monthDay = cadence == PlanCadence.MONTHLY
            ? changes.anchorDayOfMonth() != null
                ? changes.anchorDayOfMonth()
                : current.planRevision().cadence() == PlanCadence.MONTHLY
                    ? current.planRevision().anchorDayOfMonth()
                    : null
            : null;
        return requireCompleteTerms(new SpendingBudgetTerms(
            changes.desiredBalance() == null
                ? current.desiredBalance()
                : changes.desiredBalance(),
            cadence,
            weekday,
            monthDay
        ));
    }

    private SpendingBudgetTerms requireCompleteTerms(SpendingBudgetTerms terms) {
        if (terms == null || terms.desiredBalance() == null
                || terms.desiredBalance().signum() <= 0) {
            throw new BadRequestException("desiredBalance must be positive");
        }
        BigDecimal desired = currency(terms.desiredBalance(), "desiredBalance");
        try {
            PlanSchedule.validateAnchor(
                terms.cadence(), terms.anchorWeekday(), terms.anchorDayOfMonth());
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException(invalid.getMessage());
        }
        return new SpendingBudgetTerms(
            desired, terms.cadence(), terms.anchorWeekday(), terms.anchorDayOfMonth());
    }

    private BoxPlan requireBudget(Long boxId, Long planId, boolean active) {
        if (boxRepository.findByIdIncludingArchived(boxId).isEmpty()) {
            throw new NotFoundException("Box not found: " + boxId);
        }
        BoxPlan plan = active
            ? boxPlanRepository.lockById(boxId, planId)
                .filter(candidate -> candidate.status().isActive())
                .orElseThrow(() -> new NotFoundException("Active Box Plan not found: " + planId))
            : boxPlanRepository.findById(boxId, planId)
                .orElseThrow(() -> new NotFoundException("Box Plan not found: " + planId));
        if (plan.type() != BoxPlan.Type.SPENDING_BUDGET) {
            throw new NotFoundException("Spending Budget not found: " + planId);
        }
        return plan;
    }

    private BigDecimal currency(BigDecimal amount, String field) {
        try {
            BigDecimal scaled = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (scaled.precision() - scaled.scale() > 10) {
                throw new ArithmeticException();
            }
            return scaled;
        } catch (ArithmeticException invalid) {
            throw new BadRequestException(field + " must be a valid MXN amount");
        }
    }

    private BigDecimal suggestedTopUp(BigDecimal desired, BigDecimal current) {
        return desired.subtract(current).max(BigDecimal.ZERO);
    }

    private WebApplicationException conflict(String message) {
        return new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"" + message + "\"}")
                .build());
    }
}
