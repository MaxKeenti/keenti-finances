package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxHistoryEntry;
import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.BoxPlanPeriod;
import com.keenti.finances.domain.model.BoxPlanRevision;
import com.keenti.finances.domain.model.PlanCadence;
import com.keenti.finances.domain.model.PlanSchedule;
import com.keenti.finances.domain.model.SavingGoalCalculator;
import com.keenti.finances.domain.model.SavingGoalDetails;
import com.keenti.finances.domain.model.SavingGoalPeriod;
import com.keenti.finances.domain.model.SavingGoalRevision;
import com.keenti.finances.domain.model.SavingGoalRevisionPreview;
import com.keenti.finances.domain.model.SavingGoalTermsChange;
import com.keenti.finances.domain.port.in.SavingGoalUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.SavingGoalRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SavingGoalService implements SavingGoalUseCase {

    private static final Logger LOG = Logger.getLogger(SavingGoalService.class);
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int MAX_TIMELINE_PERIODS = 20_000;

    @Inject
    BoxRepository boxRepository;

    @Inject
    BoxPlanRepository boxPlanRepository;

    @Inject
    SavingGoalRepository savingGoalRepository;

    @Inject
    UserTimeZoneProvider userTimeZoneProvider;

    @Override
    @Transactional
    public List<BoxPlan> listPlans(Long boxId) {
        Box box = requireOwnedBox(boxId);
        if (!box.isArchived()) {
            boxPlanRepository.lockActiveByBoxId(boxId)
                .filter(plan -> plan.type() == BoxPlan.Type.SAVING_GOAL)
                .ifPresent(this::evaluate);
        }
        return boxPlanRepository.findAllByBoxId(boxId);
    }

    @Override
    @Transactional
    public Optional<SavingGoalDetails> getActive(Long boxId) {
        requireActiveBox(boxId);
        return boxPlanRepository.lockActiveByBoxId(boxId)
            .filter(plan -> plan.type() == BoxPlan.Type.SAVING_GOAL)
            .map(this::evaluate);
    }

    @Override
    @Transactional
    public Optional<SavingGoalDetails> get(Long boxId, Long planId) {
        Box box = requireOwnedBox(boxId);
        return boxPlanRepository.lockById(boxId, planId)
            .filter(plan -> plan.type() == BoxPlan.Type.SAVING_GOAL)
            .filter(plan -> !box.isArchived() || !plan.status().isActive())
            .map(this::evaluate);
    }

    @Override
    @Transactional
    public SavingGoalDetails create(Long boxId, SavingGoalTermsChange requested) {
        Box box = requireActiveBoxLock(boxId);
        if (boxPlanRepository.findActiveByBoxId(boxId).isPresent()) {
            throw conflict("Box already has an active plan");
        }

        LocalDate today = userTimeZoneProvider.today();
        ResolvedTerms terms = resolveCreateTerms(requested, box.getBalance(), today);
        LocalDateTime now = LocalDateTime.now();
        BoxPlan plan = boxPlanRepository.save(new BoxPlan(
            null, boxId, BoxPlan.Type.SAVING_GOAL, BoxPlan.Status.ACTIVE,
            today, now, now, null, null));

        BoxPlanRevision schedule = boxPlanRepository.saveRevision(new BoxPlanRevision(
            null, plan.id(), today, terms.cadence(), terms.anchorWeekday(),
            terms.anchorDayOfMonth(), now, null));
        savingGoalRepository.saveRevisionTerms(terms.toRevision(schedule));
        LOG.infof("saving_goal.create planId=%d boxId=%d target=%s targetDate=%s",
            plan.id(), boxId, terms.targetAmount(), terms.targetDate());
        return evaluate(plan);
    }

    @Override
    @Transactional
    public SavingGoalRevisionPreview previewRevision(Long boxId, Long planId,
                                                     SavingGoalTermsChange changes) {
        requireActiveBox(boxId);
        BoxPlan plan = requireActiveSavingGoal(boxId, planId);
        SavingGoalDetails details = evaluate(plan);
        return preview(details, changes);
    }

    @Override
    @Transactional
    public SavingGoalDetails applyRevision(Long boxId, Long planId,
                                           SavingGoalTermsChange changes) {
        requireActiveBoxLock(boxId);
        BoxPlan plan = requireActiveSavingGoal(boxId, planId);
        SavingGoalDetails details = evaluate(plan);
        SavingGoalRevisionPreview preview = preview(details, changes);
        LocalDateTime now = LocalDateTime.now();

        boxPlanRepository.supersedeUnopenedRevisions(
            plan.id(), preview.effectiveFrom(), now);
        BoxPlanRevision schedule = boxPlanRepository.saveRevision(new BoxPlanRevision(
            null,
            plan.id(),
            preview.effectiveFrom(),
            preview.cadence(),
            preview.anchorWeekday(),
            preview.anchorDayOfMonth(),
            now,
            null
        ));
        savingGoalRepository.saveRevisionTerms(new SavingGoalRevision(
            schedule.id(),
            plan.id(),
            preview.effectiveFrom(),
            preview.cadence(),
            preview.anchorWeekday(),
            preview.anchorDayOfMonth(),
            preview.targetAmount(),
            preview.targetDate(),
            preview.regularCommitment(),
            now,
            null
        ));
        boxPlanRepository.touch(plan.id(), now);
        LOG.infof("saving_goal.revision.applied planId=%d effectiveFrom=%s",
            plan.id(), preview.effectiveFrom());
        return evaluate(boxPlanRepository.findById(boxId, planId).orElseThrow());
    }

    @Override
    @Transactional
    public SavingGoalDetails confirmCompletion(Long boxId, Long planId) {
        Box box = requireActiveBoxLock(boxId);
        BoxPlan plan = requireActiveSavingGoal(boxId, planId);
        SavingGoalDetails evaluated = evaluate(plan);
        if (evaluated.plan().status() != BoxPlan.Status.READY_TO_COMPLETE) {
            throw conflict("Saving Goal is not ready to complete");
        }
        LocalDateTime now = LocalDateTime.now();
        boxPlanRepository.supersedeUnopenedRevisions(
            plan.id(), userTimeZoneProvider.today().plusDays(1), now);
        BoxPlan completed = boxPlanRepository.updateStatus(
            plan.id(), BoxPlan.Status.COMPLETED, now, box.getBalance());
        LOG.infof("saving_goal.completed planId=%d amount=%s",
            plan.id(), box.getBalance());
        return evaluate(completed);
    }

    @Override
    @Transactional
    public SavingGoalDetails abandon(Long boxId, Long planId) {
        Box box = requireActiveBoxLock(boxId);
        BoxPlan plan = requireActiveSavingGoal(boxId, planId);
        evaluate(plan);
        LocalDateTime now = LocalDateTime.now();
        boxPlanRepository.supersedeUnopenedRevisions(
            plan.id(), userTimeZoneProvider.today().plusDays(1), now);
        BoxPlan abandoned = boxPlanRepository.updateStatus(
            plan.id(), BoxPlan.Status.ABANDONED, now, box.getBalance());
        LOG.infof("saving_goal.abandoned planId=%d amount=%s",
            plan.id(), box.getBalance());
        return evaluate(abandoned);
    }

    private SavingGoalDetails evaluate(BoxPlan suppliedPlan) {
        BoxPlan plan = boxPlanRepository.findById(
                suppliedPlan.boxId(), suppliedPlan.id())
            .orElseThrow(() -> new NotFoundException("Saving Goal not found"));
        List<SavingGoalRevision> revisions = savingGoalRepository.findRevisions(
            plan.id(), false);
        if (revisions.isEmpty()) {
            throw new IllegalStateException("Saving Goal has no active revision");
        }

        Box box = plan.status().isActive()
            ? requireActiveBox(plan.boxId())
            : requireOwnedBox(plan.boxId());
        LocalDate today = userTimeZoneProvider.today();
        List<SavingGoalPeriod> periods;
        SavingGoalPeriod currentPeriod = null;

        if (plan.status().isActive()) {
            List<PeriodSpec> timeline = timeline(plan, revisions, today);
            periods = recomputeClosedPeriods(plan, timeline, boxRepository.findHistory(box.getId()), today);
            BigDecimal arrears = periods.isEmpty()
                ? ZERO
                : periods.get(periods.size() - 1).shortfall();
            currentPeriod = openPeriod(plan, timeline, revisions,
                boxRepository.findHistory(box.getId()), today, arrears);

            SavingGoalRevision effective = effectiveRevision(revisions, today);
            BoxPlan.Status calculatedStatus = calculateStatus(
                box.getBalance(), effective, today);
            if (calculatedStatus != plan.status()) {
                plan = boxPlanRepository.updateStatus(
                    plan.id(), calculatedStatus, null, null);
            }
        } else {
            periods = savingGoalRepository.findPeriods(plan.id());
        }

        SavingGoalRevision currentRevision = effectiveRevision(revisions, today);
        List<SavingGoalRevision> allRevisions = savingGoalRepository.findRevisions(
            plan.id(), true);
        BigDecimal displayBalance = !plan.status().isActive()
            && plan.completionAmount() != null
            ? plan.completionAmount()
            : box.getBalance();
        BigDecimal remaining = money(currentRevision.targetAmount()
            .subtract(displayBalance).max(BigDecimal.ZERO));
        BigDecimal arrears = periods.isEmpty()
            ? ZERO
            : periods.get(periods.size() - 1).shortfall();
        BigDecimal currentCommitment = money(
            currentRevision.regularCommitment().add(arrears));
        LocalDate projected = projectedCompletionDate(
            today, currentPeriod, currentRevision, remaining);
        LocalDate suggestedExtension = plan.status() == BoxPlan.Status.OVERDUE
            ? projected
            : null;

        return new SavingGoalDetails(
            plan,
            currentRevision,
            displayBalance,
            remaining,
            SavingGoalCalculator.progressPercent(
                displayBalance, currentRevision.targetAmount()),
            arrears,
            currentCommitment,
            projected,
            suggestedExtension,
            currentPeriod,
            List.copyOf(periods),
            List.copyOf(allRevisions)
        );
    }

    private List<SavingGoalPeriod> recomputeClosedPeriods(
            BoxPlan plan, List<PeriodSpec> timeline,
            List<BoxHistoryEntry> history, LocalDate today) {
        Map<LocalDate, SavingGoalPeriod> existing = new HashMap<>();
        for (SavingGoalPeriod period : savingGoalRepository.findPeriods(plan.id())) {
            existing.put(period.periodStart(), period);
        }

        List<SavingGoalPeriod> result = new ArrayList<>();
        BigDecimal previousShortfall = ZERO;
        for (PeriodSpec spec : timeline) {
            if (spec.range().endExclusive().isAfter(today)) {
                break;
            }
            SavingGoalPeriod calculated = calculatePeriod(
                plan, spec, history, previousShortfall,
                SavingGoalPeriod.Status.MISSED, LocalDateTime.now());
            SavingGoalPeriod stored = existing.get(spec.range().start());
            if (stored == null) {
                BoxPlanPeriod base = boxPlanRepository.savePeriod(
                    calculated.schedulePeriod());
                calculated = withId(calculated, base.id());
                savingGoalRepository.savePeriodMetrics(calculated);
            } else if (!sameOutcome(stored, calculated)) {
                calculated = withId(calculated, stored.id());
                boxPlanRepository.updatePeriod(calculated.schedulePeriod());
                savingGoalRepository.updatePeriodMetrics(calculated);
            } else {
                calculated = stored;
            }
            result.add(calculated);
            previousShortfall = calculated.shortfall();
        }
        return List.copyOf(result);
    }

    private SavingGoalPeriod openPeriod(
            BoxPlan plan, List<PeriodSpec> timeline,
            List<SavingGoalRevision> revisions,
            List<BoxHistoryEntry> history, LocalDate today,
            BigDecimal arrears) {
        for (PeriodSpec spec : timeline) {
            if (spec.range().contains(today)) {
                SavingGoalPeriod calculated = calculatePeriod(
                    plan, spec, history, arrears,
                    SavingGoalPeriod.Status.OPEN, null);
                return new SavingGoalPeriod(
                    null,
                    calculated.planId(),
                    calculated.revisionId(),
                    calculated.periodStart(),
                    calculated.periodEndExclusive(),
                    calculated.openingBalance(),
                    balanceBefore(history, today.plusDays(1)),
                    money(balanceBefore(history, today.plusDays(1))
                        .subtract(calculated.openingBalance())),
                    calculated.regularCommitment(),
                    calculated.openingArrears(),
                    calculated.requiredAmount(),
                    calculated.arrearsCovered(),
                    calculated.regularProgress(),
                    calculated.extraProgress(),
                    calculated.shortfall(),
                    SavingGoalPeriod.Status.OPEN,
                    null
                );
            }
        }
        return null;
    }

    private SavingGoalPeriod calculatePeriod(
            BoxPlan plan, PeriodSpec spec, List<BoxHistoryEntry> history,
            BigDecimal openingArrears, SavingGoalPeriod.Status forcedStatus,
            LocalDateTime evaluatedAt) {
        BigDecimal opening = spec.range().start().equals(plan.startDate())
            ? balanceAtPlanCreation(history, plan)
            : balanceBefore(history, spec.range().start());
        BigDecimal closing = balanceBefore(history, spec.range().endExclusive());
        BigDecimal progress = money(closing.subtract(opening));
        SavingGoalCalculator.ProgressAllocation allocation =
            SavingGoalCalculator.allocate(
                progress, spec.revision().regularCommitment(), openingArrears);
        SavingGoalPeriod.Status status = forcedStatus == SavingGoalPeriod.Status.OPEN
            ? SavingGoalPeriod.Status.OPEN
            : allocation.status();
        return new SavingGoalPeriod(
            null,
            plan.id(),
            spec.revision().id(),
            spec.range().start(),
            spec.range().endExclusive(),
            opening,
            closing,
            progress,
            spec.revision().regularCommitment(),
            money(openingArrears),
            allocation.requiredAmount(),
            allocation.arrearsCovered(),
            allocation.regularProgress(),
            allocation.extraProgress(),
            allocation.shortfall(),
            status,
            evaluatedAt
        );
    }

    private List<PeriodSpec> timeline(BoxPlan plan,
                                      List<SavingGoalRevision> revisions,
                                      LocalDate throughDate) {
        List<SavingGoalRevision> ordered = revisions.stream()
            .sorted(Comparator.comparing(SavingGoalRevision::effectiveFrom)
                .thenComparing(SavingGoalRevision::createdAt)
                .thenComparing(SavingGoalRevision::id))
            .toList();
        List<PeriodSpec> result = new ArrayList<>();
        LocalDate cursor = plan.startDate();
        for (int guard = 0; guard < MAX_TIMELINE_PERIODS; guard++) {
            LocalDate periodStart = cursor;
            SavingGoalRevision revision = effectiveRevision(ordered, cursor);
            LocalDate targetEnd = revision.targetDate().plusDays(1);
            if (!cursor.isBefore(targetEnd)) {
                Optional<SavingGoalRevision> future = ordered.stream()
                    .filter(candidate -> candidate.effectiveFrom().isAfter(periodStart))
                    .findFirst();
                if (future.isEmpty() || future.get().effectiveFrom().isAfter(throughDate)) {
                    break;
                }
                cursor = future.get().effectiveFrom();
                continue;
            }
            LocalDate end = PlanSchedule.nextBoundary(
                cursor, revision.cadence(), revision.anchorWeekday(),
                revision.anchorDayOfMonth());
            if (end.isAfter(targetEnd)) {
                end = targetEnd;
            }
            LocalDate scheduledEnd = end;
            Optional<LocalDate> nextRevision = ordered.stream()
                .map(SavingGoalRevision::effectiveFrom)
                .filter(date -> date.isAfter(periodStart) && date.isBefore(scheduledEnd))
                .findFirst();
            if (nextRevision.isPresent()) {
                end = nextRevision.get();
            }
            PlanSchedule.DateRange range = new PlanSchedule.DateRange(cursor, end);
            result.add(new PeriodSpec(range, revision));
            if (range.contains(throughDate) || cursor.isAfter(throughDate)) {
                break;
            }
            cursor = end;
        }
        if (result.size() >= MAX_TIMELINE_PERIODS) {
            throw new IllegalStateException("Saving Goal timeline exceeds safe bounds");
        }
        return List.copyOf(result);
    }

    private SavingGoalRevisionPreview preview(SavingGoalDetails details,
                                              SavingGoalTermsChange changes) {
        if (changes == null) {
            throw new BadRequestException("Revision changes are required");
        }
        LocalDate today = userTimeZoneProvider.today();
        LocalDate effectiveFrom = details.currentPeriod() != null
            ? details.currentPeriod().periodEndExclusive()
            : today;
        SavingGoalRevision current = details.currentRevision();

        BigDecimal target = changes.targetAmount() != null
            ? validPositiveMoney(changes.targetAmount(), "targetAmount")
            : current.targetAmount();
        LocalDate targetDate = changes.targetDate() != null
            ? changes.targetDate()
            : current.targetDate();
        if (targetDate.isBefore(effectiveFrom)) {
            throw new BadRequestException(
                "targetDate must be on or after the revision effective date");
        }
        PlanCadence cadence = changes.cadence() != null
            ? changes.cadence()
            : current.cadence();
        Integer anchorWeekday = resolvedWeekday(changes, current, cadence);
        Integer anchorDay = resolvedMonthDay(changes, current, cadence);
        validateAnchor(cadence, anchorWeekday, anchorDay);

        BigDecimal remaining = money(target.subtract(details.boxBalance())
            .max(BigDecimal.ZERO));
        int remainingPeriods = PlanSchedule.countPeriods(
            effectiveFrom, targetDate, cadence, anchorWeekday, anchorDay);
        BigDecimal regular = changes.regularCommitment() != null
            ? validCommitment(changes.regularCommitment(), remaining)
            : SavingGoalCalculator.defaultCommitment(remaining, remainingPeriods);
        LocalDate projected = projectedCompletionDate(
            effectiveFrom, null,
            new SavingGoalRevision(null, details.plan().id(), effectiveFrom,
                cadence, anchorWeekday, anchorDay, target, targetDate,
                regular, LocalDateTime.now(), null),
            remaining);
        LocalDate extension = projected != null && projected.isAfter(targetDate)
            ? projected
            : null;
        return new SavingGoalRevisionPreview(
            effectiveFrom, target, targetDate, cadence,
            anchorWeekday, anchorDay, regular, remainingPeriods,
            details.boxBalance(), remaining, details.arrears(), projected, extension);
    }

    private ResolvedTerms resolveCreateTerms(SavingGoalTermsChange requested,
                                             BigDecimal boxBalance,
                                             LocalDate today) {
        if (requested == null || requested.targetAmount() == null
            || requested.targetDate() == null || requested.cadence() == null) {
            throw new BadRequestException(
                "targetAmount, targetDate, and cadence are required");
        }
        BigDecimal target = validPositiveMoney(
            requested.targetAmount(), "targetAmount");
        if (requested.targetDate().isBefore(today)) {
            throw new BadRequestException("targetDate cannot be in the past");
        }
        validateAnchor(requested.cadence(), requested.anchorWeekday(),
            requested.anchorDayOfMonth());
        BigDecimal remaining = money(target.subtract(boxBalance)
            .max(BigDecimal.ZERO));
        int periodCount = PlanSchedule.countPeriods(
            today, requested.targetDate(), requested.cadence(),
            requested.anchorWeekday(), requested.anchorDayOfMonth());
        BigDecimal regular = requested.regularCommitment() == null
            ? SavingGoalCalculator.defaultCommitment(remaining, periodCount)
            : validCommitment(requested.regularCommitment(), remaining);
        return new ResolvedTerms(
            target, requested.targetDate(), requested.cadence(),
            requested.anchorWeekday(), requested.anchorDayOfMonth(), regular);
    }

    private LocalDate projectedCompletionDate(
            LocalDate today, SavingGoalPeriod currentPeriod,
            SavingGoalRevision revision, BigDecimal remaining) {
        int periods = SavingGoalCalculator.periodsNeeded(
            remaining, revision.regularCommitment());
        if (remaining.signum() == 0) {
            return today;
        }
        if (periods == 0) {
            return null;
        }
        LocalDate cursor = currentPeriod != null
            ? currentPeriod.periodStart()
            : today;
        LocalDate end = currentPeriod != null
            ? currentPeriod.periodEndExclusive()
            : PlanSchedule.nextBoundary(cursor, revision.cadence(),
                revision.anchorWeekday(), revision.anchorDayOfMonth());
        for (int index = 1; index < periods; index++) {
            cursor = end;
            end = PlanSchedule.nextBoundary(cursor, revision.cadence(),
                revision.anchorWeekday(), revision.anchorDayOfMonth());
        }
        return end.minusDays(1);
    }

    private SavingGoalRevision effectiveRevision(
            List<SavingGoalRevision> revisions, LocalDate date) {
        return revisions.stream()
            .filter(revision -> !revision.effectiveFrom().isAfter(date))
            .max(Comparator.comparing(SavingGoalRevision::effectiveFrom)
                .thenComparing(SavingGoalRevision::createdAt)
                .thenComparing(SavingGoalRevision::id))
            .orElseGet(() -> revisions.stream()
                .min(Comparator.comparing(SavingGoalRevision::effectiveFrom))
                .orElseThrow());
    }

    private BoxPlan.Status calculateStatus(BigDecimal boxBalance,
                                           SavingGoalRevision revision,
                                           LocalDate today) {
        if (boxBalance.compareTo(revision.targetAmount()) >= 0) {
            return BoxPlan.Status.READY_TO_COMPLETE;
        }
        if (today.isAfter(revision.targetDate())) {
            return BoxPlan.Status.OVERDUE;
        }
        return BoxPlan.Status.ACTIVE;
    }

    private BigDecimal balanceAtPlanCreation(List<BoxHistoryEntry> history,
                                             BoxPlan plan) {
        return money(history.stream()
            .filter(entry -> entry.effectiveDate().isBefore(plan.startDate())
                || (entry.effectiveDate().equals(plan.startDate())
                    && !entry.createdAt().isAfter(plan.createdAt())))
            .map(BoxHistoryEntry::signedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal balanceBefore(List<BoxHistoryEntry> history,
                                     LocalDate boundary) {
        return money(history.stream()
            .filter(entry -> entry.effectiveDate().isBefore(boundary))
            .map(BoxHistoryEntry::signedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private boolean sameOutcome(SavingGoalPeriod left, SavingGoalPeriod right) {
        return left.revisionId().equals(right.revisionId())
            && left.periodEndExclusive().equals(right.periodEndExclusive())
            && sameMoney(left.openingBalance(), right.openingBalance())
            && sameMoney(left.closingBalance(), right.closingBalance())
            && sameMoney(left.netProgress(), right.netProgress())
            && sameMoney(left.regularCommitment(), right.regularCommitment())
            && sameMoney(left.openingArrears(), right.openingArrears())
            && sameMoney(left.requiredAmount(), right.requiredAmount())
            && sameMoney(left.arrearsCovered(), right.arrearsCovered())
            && sameMoney(left.regularProgress(), right.regularProgress())
            && sameMoney(left.extraProgress(), right.extraProgress())
            && sameMoney(left.shortfall(), right.shortfall())
            && left.status() == right.status();
    }

    private SavingGoalPeriod withId(SavingGoalPeriod period, Long id) {
        return new SavingGoalPeriod(
            id, period.planId(), period.revisionId(), period.periodStart(),
            period.periodEndExclusive(), period.openingBalance(),
            period.closingBalance(), period.netProgress(),
            period.regularCommitment(), period.openingArrears(),
            period.requiredAmount(), period.arrearsCovered(),
            period.regularProgress(), period.extraProgress(),
            period.shortfall(), period.status(), period.evaluatedAt());
    }

    private Box requireActiveBox(Long boxId) {
        return boxRepository.findActiveById(boxId)
            .orElseThrow(() -> new NotFoundException("Box not found: " + boxId));
    }

    private Box requireOwnedBox(Long boxId) {
        return boxRepository.findByIdIncludingArchived(boxId)
            .orElseThrow(() -> new NotFoundException("Box not found: " + boxId));
    }

    private Box requireActiveBoxLock(Long boxId) {
        return boxRepository.lockActiveById(boxId)
            .orElseThrow(() -> new NotFoundException("Box not found: " + boxId));
    }

    private BoxPlan requireActiveSavingGoal(Long boxId, Long planId) {
        return boxPlanRepository.lockById(boxId, planId)
            .filter(plan -> plan.status().isActive())
            .filter(plan -> plan.type() == BoxPlan.Type.SAVING_GOAL)
            .orElseThrow(() -> new NotFoundException("Active Saving Goal not found"));
    }

    private static BigDecimal validPositiveMoney(BigDecimal value, String field) {
        BigDecimal amount = validMoney(value, field);
        if (amount.signum() <= 0) {
            throw new BadRequestException(field + " must be greater than zero");
        }
        return amount;
    }

    private static BigDecimal validCommitment(BigDecimal value,
                                              BigDecimal remaining) {
        BigDecimal amount = validMoney(value, "regularCommitment");
        if (amount.signum() < 0 || (remaining.signum() > 0 && amount.signum() == 0)) {
            throw new BadRequestException(
                "regularCommitment must be greater than zero while money remains");
        }
        return amount;
    }

    private static BigDecimal validMoney(BigDecimal value, String field) {
        if (value == null) {
            throw new BadRequestException(field + " is required");
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > 2
            || Math.max(stripped.precision() - stripped.scale(), 0) > 10) {
            throw new BadRequestException(field + " must be a valid MXN amount");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void validateAnchor(PlanCadence cadence,
                                       Integer anchorWeekday,
                                       Integer anchorDayOfMonth) {
        try {
            PlanSchedule.validateAnchor(cadence, anchorWeekday, anchorDayOfMonth);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }

    private static Integer resolvedWeekday(SavingGoalTermsChange changes,
                                           SavingGoalRevision current,
                                           PlanCadence cadence) {
        if (cadence != PlanCadence.WEEKLY) {
            return null;
        }
        if (changes.anchorWeekday() != null) {
            return changes.anchorWeekday();
        }
        return current.cadence() == PlanCadence.WEEKLY
            ? current.anchorWeekday()
            : null;
    }

    private static Integer resolvedMonthDay(SavingGoalTermsChange changes,
                                            SavingGoalRevision current,
                                            PlanCadence cadence) {
        if (cadence != PlanCadence.MONTHLY) {
            return null;
        }
        if (changes.anchorDayOfMonth() != null) {
            return changes.anchorDayOfMonth();
        }
        return current.cadence() == PlanCadence.MONTHLY
            ? current.anchorDayOfMonth()
            : null;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static boolean sameMoney(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) == 0;
    }

    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"" + message + "\"}")
                .build());
    }

    private record PeriodSpec(
        PlanSchedule.DateRange range,
        SavingGoalRevision revision
    ) {}

    private record ResolvedTerms(
        BigDecimal targetAmount,
        LocalDate targetDate,
        PlanCadence cadence,
        Integer anchorWeekday,
        Integer anchorDayOfMonth,
        BigDecimal regularCommitment
    ) {
        SavingGoalRevision toRevision(BoxPlanRevision schedule) {
            return new SavingGoalRevision(
                schedule.id(), schedule.planId(), schedule.effectiveFrom(),
                schedule.cadence(), schedule.anchorWeekday(),
                schedule.anchorDayOfMonth(), targetAmount, targetDate,
                regularCommitment, schedule.createdAt(), schedule.supersededAt());
        }
    }
}
