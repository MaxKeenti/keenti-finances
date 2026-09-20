package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.BoxFunding;
import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.DebtPayment;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.in.DebtUseCase;
import com.keenti.finances.domain.port.in.TransactionUseCase;
import com.keenti.finances.domain.port.out.DebtPaymentRepository;
import com.keenti.finances.domain.port.out.DebtRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DebtService implements DebtUseCase {

    private static final Logger LOG = Logger.getLogger(DebtService.class);

    /**
     * A Debt's Direction is the Direction of the Transactions its Debt Payments
     * create: INGRESS for money owed to the User, EGRESS for money the User owes.
     */
    private static final Set<String> VALID_DIRECTIONS = Set.of("INGRESS", "EGRESS");

    @Inject
    DebtRepository debtRepository;

    @Inject
    DebtPaymentRepository debtPaymentRepository;

    @Inject
    TransactionUseCase transactionUseCase;

    @Override
    public List<Debt> list() {
        List<Debt> debts = debtRepository.findAll();
        LOG.infof("debt.list count=%d", debts.size());
        return debts;
    }

    @Override
    public Optional<Debt> getById(Long id) {
        Optional<Debt> result = debtRepository.findById(id);
        LOG.infof("debt.get id=%d found=%b", id, result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public Debt create(Debt debt) {
        validateDirection(debt.getDirection());
        Debt created = debtRepository.save(debt);
        LOG.infof("debt.create id=%d contactId=%d direction=%s amount=%s",
            created.getId(), created.getContactId(), created.getDirection(), created.getTotalAmount());
        return created;
    }

    @Override
    @Transactional
    public Debt update(Long id, Debt debt) {
        Debt existing = debtRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Debt not found: " + id));
        validateDirection(debt.getDirection());

        // Flipping the Direction would contradict Transactions that already
        // moved money the other way (ADR-0005), and those are not reversed here.
        // Recorded payments therefore freeze the Direction.
        if (!existing.getDirection().equals(debt.getDirection())
                && !debtPaymentRepository.findByDebtId(id).isEmpty()) {
            throw new BadRequestException(
                "Cannot change the direction of a debt with recorded payments: " + id);
        }

        Debt updated = debtRepository.update(new Debt(
            id, debt.getContactId(), debt.getDirection(), debt.getDescription(), debt.getTotalAmount(),
            debt.getStatus(), debt.getCreatedAt()));
        LOG.infof("debt.update id=%d direction=%s", id, updated.getDirection());
        return updated;
    }

    private void validateDirection(String direction) {
        if (direction == null || !VALID_DIRECTIONS.contains(direction)) {
            throw new BadRequestException(
                "Invalid direction: " + direction + ". Must be INGRESS or EGRESS");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        debtRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Debt not found: " + id));
        debtRepository.softDeleteById(id);
        LOG.infof("debt.soft_deleted id=%d", id);
    }

    @Override
    @Transactional
    public void restore(Long id) {
        debtRepository.findDeletedById(id).orElseThrow(() ->
            new NotFoundException("Deleted debt not found: " + id));
        debtRepository.restoreById(id);
        LOG.infof("debt.restored id=%d", id);
    }

    @Override
    @Transactional
    public void permanentDelete(Long id) {
        debtRepository.findDeletedById(id).orElseThrow(() ->
            new NotFoundException("Deleted debt not found: " + id));
        debtRepository.deleteById(id);
        LOG.infof("debt.permanent_deleted id=%d", id);
    }

    @Override
    public List<TrashItem> listDeleted() {
        List<TrashItem> items = debtRepository.findAllDeleted();
        LOG.infof("debt.trash.list count=%d", items.size());
        return items;
    }

    @Override
    @Transactional
    public DebtPayment recordPayment(Long debtId, BigDecimal amount, LocalDate paymentDate,
                                     Long categoryId, Long accountId, String notes,
                                     List<BoxFunding> boxFunding) {
        Debt debt = debtRepository.findById(debtId).orElseThrow(() ->
            new NotFoundException("Debt not found: " + debtId));

        if (!"ACTIVE".equals(debt.getStatus())) {
            throw new BadRequestException("Cannot record payment on a PAID debt: " + debtId);
        }

        List<BoxFunding> funding = boxFunding == null ? List.of() : boxFunding;
        // Money coming in is not spending, so there is nothing for a Box to fund.
        // Saying so here beats letting TransactionService reject it with a message
        // about a Transaction the User never asked for by name.
        if (!funding.isEmpty() && !"EGRESS".equals(debt.getDirection())) {
            throw new BadRequestException(
                "Box funding applies only to payments on a debt the user owes: " + debtId);
        }

        BigDecimal paid = debtPaymentRepository.sumByDebtId(debtId);
        BigDecimal remaining = debt.getTotalAmount().subtract(paid);

        if (amount.compareTo(remaining) > 0) {
            throw new BadRequestException(
                String.format("Payment amount %s exceeds remaining balance %s for debt %d",
                    amount, remaining, debtId));
        }

        // ADR-0005, extended per ADR-0023: the Transaction takes the Debt's own
        // Direction, so settling what the User owes moves money out rather than in.
        Transaction tx = transactionUseCase.create(new Transaction(
            null, amount, debt.getDirection(),
            "Debt payment: " + debt.getDescription(),
            paymentDate, categoryId, debt.getContactId(), null, accountId,
            funding, List.of()));

        DebtPayment payment = debtPaymentRepository.save(new DebtPayment(
            null, debtId, amount, paymentDate, tx.getId(), notes, null));

        BigDecimal newRemaining = remaining.subtract(amount);
        if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
            debtRepository.update(new Debt(
                debt.getId(), debt.getContactId(), debt.getDirection(), debt.getDescription(),
                debt.getTotalAmount(), "PAID", debt.getCreatedAt()));
            LOG.infof("debt.status.paid id=%d", debtId);
        }

        LOG.infof("debt.payment.record debtId=%d direction=%s paymentId=%d amount=%s remaining=%s transactionId=%d boxFundingCount=%d",
            debtId, debt.getDirection(), payment.getId(), amount, newRemaining, tx.getId(),
            funding.size());

        return payment;
    }

    @Override
    public List<DebtPayment> listPayments(Long debtId) {
        debtRepository.findById(debtId).orElseThrow(() ->
            new NotFoundException("Debt not found: " + debtId));
        return debtPaymentRepository.findByDebtId(debtId);
    }

    public BigDecimal getRemainingBalance(Long debtId) {
        Debt debt = debtRepository.findById(debtId).orElseThrow(() ->
            new NotFoundException("Debt not found: " + debtId));
        BigDecimal paid = debtPaymentRepository.sumByDebtId(debtId);
        return debt.getTotalAmount().subtract(paid);
    }

    @Override
    @Transactional
    public BulkPaymentResult bulkPayment(Long contactId, String direction, BigDecimal totalAmount,
                                          LocalDate paymentDate, Long categoryId, Long accountId,
                                          String notes) {
        validateDirection(direction);
        // One lump sum settles one side of the relationship. Debts in the other
        // direction are a separate balance and are never netted against it here.
        List<Debt> activeDebts =
            debtRepository.findActiveByContactIdOrderByCreatedAt(contactId, direction);

        if (activeDebts.isEmpty()) {
            throw new BadRequestException(
                "No active " + direction + " debts found for contact: " + contactId);
        }

        BigDecimal remaining = totalAmount;
        List<BulkPaymentItem> items = new ArrayList<>();

        for (Debt debt : activeDebts) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal debtRemaining = debt.getTotalAmount().subtract(debtPaymentRepository.sumByDebtId(debt.getId()));
            if (debtRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal apply = remaining.min(debtRemaining);

            Transaction tx = transactionUseCase.create(new Transaction(
                null, apply, debt.getDirection(),
                "Bulk payment: " + debt.getDescription(),
                paymentDate, categoryId, contactId, null, accountId,
                List.of(), List.of()));

            debtPaymentRepository.save(new DebtPayment(
                null, debt.getId(), apply, paymentDate, tx.getId(), notes, null));

            BigDecimal newRemaining = debtRemaining.subtract(apply);
            String newStatus = debt.getStatus();
            if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                newStatus = "PAID";
                debtRepository.update(new Debt(
                    debt.getId(), debt.getContactId(), debt.getDirection(), debt.getDescription(),
                    debt.getTotalAmount(), "PAID", debt.getCreatedAt()));
                LOG.infof("bulk.payment.debt.paid debtId=%d", debt.getId());
            }

            items.add(new BulkPaymentItem(debt.getId(), debt.getDescription(), apply, newRemaining, newStatus));
            remaining = remaining.subtract(apply);
        }

        BigDecimal totalApplied = totalAmount.subtract(remaining);
        LOG.infof("bulk.payment contactId=%d direction=%s totalAmount=%s applied=%s unused=%s debtsProcessed=%d",
            contactId, direction, totalAmount, totalApplied, remaining, items.size());

        return new BulkPaymentResult(contactId, direction, totalAmount, totalApplied, remaining, items);
    }
}
