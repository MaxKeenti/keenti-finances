package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxBalanceSummary;
import com.keenti.finances.domain.model.BoxCommandResult;
import com.keenti.finances.domain.model.BoxHistoryEntry;
import com.keenti.finances.domain.model.BoxMovement;
import com.keenti.finances.domain.model.BoxTransferResult;
import com.keenti.finances.domain.port.in.BoxUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.TransactionRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BoxService implements BoxUseCase {

    private static final Logger LOG = Logger.getLogger(BoxService.class);
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_ICON_LENGTH = 16;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_INTEGER_DIGITS = 10;

    @Inject
    BoxRepository boxRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    FinancialAccountRepository financialAccountRepository;

    @Inject
    BoxPlanRepository boxPlanRepository;

    @Inject
    UserTimeZoneProvider userTimeZoneProvider;

    @Override
    @Transactional
    public List<Box> list(boolean archived) {
        List<Box> boxes = boxRepository.findAll(archived);
        LOG.infof("box.list archived=%b count=%d", archived, boxes.size());
        return boxes;
    }

    @Override
    @Transactional
    public Optional<Box> getById(Long id) {
        Optional<Box> box = boxRepository.findByIdIncludingArchived(id);
        LOG.infof("box.get id=%d found=%b", id, box.isPresent());
        return box;
    }

    @Override
    @Transactional
    public List<BoxHistoryEntry> history(Long id) {
        requireReadable(id);
        List<BoxHistoryEntry> history = boxRepository.findHistory(id);
        LOG.infof("box.history id=%d count=%d", id, history.size());
        return history;
    }

    @Override
    @Transactional
    public BoxBalanceSummary summary() {
        BigDecimal netBalance = financialAccountRepository.isTrackingActive()
            ? financialAccountRepository.getTotalBalance()
            : transactionRepository.getNetBalance();
        BigDecimal inBoxes = boxRepository.getTotalBalance();
        return new BoxBalanceSummary(
            netBalance,
            inBoxes,
            netBalance.subtract(inBoxes)
        );
    }

    @Override
    @Transactional
    public Box create(Box requested) {
        Box normalized = normalizedBox(requested);
        boxRepository.lockAllocationScope();
        ensureNameAvailable(normalized.getName(), null);

        Box created = boxRepository.save(new Box(
            null,
            normalized.getName(),
            normalized.getHue(),
            normalized.getIcon(),
            normalized.getDescription(),
            boxRepository.nextDisplayOrder(),
            BigDecimal.ZERO,
            false,
            null,
            null,
            0
        ));
        LOG.infof("box.create id=%d name=%s", created.getId(), created.getName());
        return created;
    }

    @Override
    @Transactional
    public Box update(Long id, Box requested) {
        Box normalized = normalizedBox(requested);
        boxRepository.lockAllocationScope();
        Box existing = requireActiveLock(id);
        ensureNameAvailable(normalized.getName(), id);

        Box updated = boxRepository.update(new Box(
            id,
            normalized.getName(),
            normalized.getHue(),
            normalized.getIcon(),
            normalized.getDescription(),
            existing.getDisplayOrder(),
            existing.getBalance(),
            false,
            existing.getCreatedAt(),
            existing.getUpdatedAt(),
            existing.getVersion()
        ));
        LOG.infof("box.update id=%d", id);
        return updated;
    }

    @Override
    @Transactional
    public List<Box> reorder(List<Long> boxIds) {
        if (boxIds == null) {
            throw new BadRequestException("boxIds is required");
        }
        boxRepository.lockAllocationScope();

        Set<Long> requestedIds = new HashSet<>(boxIds);
        if (requestedIds.size() != boxIds.size() || requestedIds.contains(null)) {
            throw new BadRequestException("boxIds must contain unique non-null identifiers");
        }
        for (Long id : boxIds) {
            if (boxRepository.findActiveById(id).isEmpty()) {
                throw new NotFoundException("Box not found: " + id);
            }
        }

        Set<Long> activeIds = boxRepository.findAll(false).stream()
            .map(Box::getId)
            .collect(java.util.stream.Collectors.toSet());
        if (!requestedIds.equals(activeIds)) {
            throw new BadRequestException("boxIds must contain every active Box exactly once");
        }

        List<Box> reordered = boxRepository.reorder(boxIds);
        LOG.infof("box.reorder count=%d", reordered.size());
        return reordered;
    }

    @Override
    @Transactional
    public BoxCommandResult deposit(Long id, BigDecimal requestedAmount,
                                    LocalDate effectiveDate) {
        BigDecimal amount = validAmount(requestedAmount);
        validEffectiveDate(effectiveDate);

        boxRepository.lockAllocationScope();
        requireActiveLock(id);
        BoxBalanceSummary before = summary();
        if (amount.compareTo(before.availableToSpend()) > 0) {
            throw new BadRequestException("Deposit exceeds Available to Spend");
        }

        LocalDateTime createdAt = LocalDateTime.now();
        boxRepository.saveMovement(new BoxMovement(
            null,
            BoxMovement.Type.DEPOSIT,
            null,
            id,
            amount,
            effectiveDate,
            createdAt
        ));
        Box updated = boxRepository.findActiveById(id).orElseThrow();
        BoxBalanceSummary after = summary();
        LOG.infof("box.deposit id=%d amount=%s effectiveDate=%s",
            id, amount, effectiveDate);
        return new BoxCommandResult(updated, after);
    }

    @Override
    @Transactional
    public BoxCommandResult withdraw(Long id, BigDecimal requestedAmount,
                                     LocalDate effectiveDate) {
        BigDecimal amount = validAmount(requestedAmount);
        validEffectiveDate(effectiveDate);

        requireActiveLock(id);
        LocalDateTime createdAt = LocalDateTime.now();
        ensureCanDebit(id, amount, effectiveDate, createdAt);
        boxRepository.saveMovement(new BoxMovement(
            null,
            BoxMovement.Type.WITHDRAWAL,
            id,
            null,
            amount,
            effectiveDate,
            createdAt
        ));
        Box updated = boxRepository.findActiveById(id).orElseThrow();
        BoxBalanceSummary after = summary();
        LOG.infof("box.withdraw id=%d amount=%s effectiveDate=%s",
            id, amount, effectiveDate);
        return new BoxCommandResult(updated, after);
    }

    @Override
    @Transactional
    public BoxTransferResult transfer(Long sourceBoxId, Long targetBoxId,
                                      BigDecimal requestedAmount,
                                      LocalDate effectiveDate) {
        BigDecimal amount = validAmount(requestedAmount);
        validEffectiveDate(effectiveDate);
        if (sourceBoxId.equals(targetBoxId)) {
            throw new BadRequestException("Source and target Boxes must be different");
        }

        Long firstId = Math.min(sourceBoxId, targetBoxId);
        Long secondId = Math.max(sourceBoxId, targetBoxId);
        requireActiveLock(firstId);
        requireActiveLock(secondId);

        LocalDateTime createdAt = LocalDateTime.now();
        ensureCanDebit(sourceBoxId, amount, effectiveDate, createdAt);
        boxRepository.saveMovement(new BoxMovement(
            null,
            BoxMovement.Type.TRANSFER,
            sourceBoxId,
            targetBoxId,
            amount,
            effectiveDate,
            createdAt
        ));

        Box source = boxRepository.findActiveById(sourceBoxId).orElseThrow();
        Box target = boxRepository.findActiveById(targetBoxId).orElseThrow();
        BoxBalanceSummary after = summary();
        LOG.infof("box.transfer sourceId=%d targetId=%d amount=%s effectiveDate=%s",
            sourceBoxId, targetBoxId, amount, effectiveDate);
        return new BoxTransferResult(source, target, after);
    }

    @Override
    @Transactional
    public BoxCommandResult correctMovement(
            Long boxId, Long movementId, BigDecimal requestedAmount,
            LocalDate effectiveDate) {
        BigDecimal amount = validAmount(requestedAmount);
        validEffectiveDate(effectiveDate);

        // Corrections can increase the total allocation even when they edit a
        // withdrawal. Serialize that check with deposits and INGRESS
        // distributions before taking the movement and Box locks.
        boxRepository.lockAllocationScope();
        BoxMovement existing = boxRepository.lockMovementByIdForBox(
                movementId, boxId)
            .orElseThrow(() -> new NotFoundException(
                "Box Movement not found: " + movementId));
        List<Long> affectedBoxIds = java.util.stream.Stream.of(
                existing.sourceBoxId(), existing.destinationBoxId())
            .filter(java.util.Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        for (Long affectedBoxId : affectedBoxIds) {
            Box affected = boxRepository.lockByIdIncludingArchived(affectedBoxId)
                .orElseThrow(() -> new NotFoundException(
                    "Box not found: " + affectedBoxId));
            if (affected.isArchived()) {
                throw conflict(
                    "Every affected Box must be restored before correcting a movement");
            }
        }

        BoxMovement replacement = new BoxMovement(
            existing.id(), existing.type(), existing.sourceBoxId(),
            existing.destinationBoxId(), amount, effectiveDate,
            existing.createdAt(), existing.sourceTransactionId(),
            existing.sourceTransactionReference(), existing.sourceTransactionOrder(),
            existing.sourceTransactionChanged());

        BigDecimal allocationIncrease = allocationContribution(replacement)
            .subtract(allocationContribution(existing));
        if (allocationIncrease.signum() > 0
                && allocationIncrease.compareTo(summary().availableToSpend()) > 0) {
            throw new BadRequestException(
                "Correction would allocate more than Available to Spend");
        }
        if (!boxRepository.replacementRemainsNonNegative(replacement)) {
            throw new BadRequestException(
                "Correction would make an affected Box negative at its effective date or later");
        }

        boxRepository.updateMovement(replacement);
        Box correctedBox = boxRepository.findActiveById(boxId).orElseThrow();
        BoxBalanceSummary after = summary();
        LOG.infof(
            "box.movement.corrected boxId=%d movementId=%d amount=%s effectiveDate=%s",
            boxId, movementId, amount, effectiveDate);
        return new BoxCommandResult(correctedBox, after);
    }

    @Override
    @Transactional
    public Box archive(Long id) {
        boxRepository.lockAllocationScope();
        Box box = requireActiveLock(id);
        if (boxPlanRepository.findActiveByBoxId(id).isPresent()) {
            throw conflict(
                "Complete, abandon, or end the active Box Plan before archiving");
        }
        if (boxRepository.getBalance(id).signum() != 0) {
            throw new BadRequestException("Withdraw the full Box balance before archiving");
        }
        Box archived = boxRepository.setArchived(id, true, box.getDisplayOrder());
        LOG.infof("box.archive id=%d", id);
        return archived;
    }

    @Override
    @Transactional
    public Box restore(Long id) {
        boxRepository.lockAllocationScope();
        Box box = boxRepository.lockByIdIncludingArchived(id)
            .filter(Box::isArchived)
            .orElseThrow(() -> new NotFoundException("Archived Box not found: " + id));
        ensureNameAvailable(box.getName(), id);

        Box restored = boxRepository.setArchived(id, false, boxRepository.nextDisplayOrder());
        LOG.infof("box.restore id=%d", id);
        return restored;
    }

    private Box requireReadable(Long id) {
        return boxRepository.findByIdIncludingArchived(id)
            .orElseThrow(() -> new NotFoundException("Box not found: " + id));
    }

    private Box requireActiveLock(Long id) {
        return boxRepository.lockActiveById(id)
            .orElseThrow(() -> new NotFoundException("Box not found: " + id));
    }

    private void ensureCanDebit(Long id, BigDecimal amount, LocalDate effectiveDate,
                                LocalDateTime createdAt) {
        if (!boxRepository.canDebit(id, amount, effectiveDate, createdAt)) {
            throw new BadRequestException(
                "Movement would make the Box balance negative at its effective date or later");
        }
    }

    private void ensureNameAvailable(String name, Long excludingId) {
        if (boxRepository.existsActiveByName(name, excludingId)) {
            throw conflict("Box name already exists");
        }
    }

    private BigDecimal allocationContribution(BoxMovement movement) {
        return switch (movement.type()) {
            case DEPOSIT -> movement.amount();
            case WITHDRAWAL -> movement.amount().negate();
            case TRANSFER -> BigDecimal.ZERO.setScale(2);
        };
    }

    private Box normalizedBox(Box box) {
        if (box == null || box.getName() == null || box.getName().trim().isEmpty()) {
            throw new BadRequestException("Box name is required");
        }
        String name = box.getName().trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("Box name must be at most 100 characters");
        }
        if (box.getHue() < 0 || box.getHue() >= 360) {
            throw new BadRequestException("Box hue must be between 0 and 359");
        }

        String icon = trimToNull(box.getIcon());
        if (icon != null && icon.length() > MAX_ICON_LENGTH) {
            throw new BadRequestException("Box icon must be at most 16 characters");
        }
        String description = trimToNull(box.getDescription());
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException("Box description must be at most 500 characters");
        }
        return new Box(
            box.getId(), name, box.getHue(), icon, description,
            box.getDisplayOrder(), box.getBalance(), box.isArchived(),
            box.getCreatedAt(), box.getUpdatedAt(), box.getVersion()
        );
    }

    private BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new BadRequestException("Amount may have at most two decimal places");
        }
        int integerDigits = Math.max(stripped.precision() - stripped.scale(), 0);
        if (integerDigits > MAX_INTEGER_DIGITS) {
            throw new BadRequestException("Amount is too large");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void validEffectiveDate(LocalDate effectiveDate) {
        if (effectiveDate == null) {
            throw new BadRequestException("effectiveDate is required");
        }
        if (effectiveDate.isAfter(userTimeZoneProvider.today())) {
            throw new BadRequestException("Future-dated Box Movements are not allowed");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"" + message + "\"}")
                .build()
        );
    }
}
