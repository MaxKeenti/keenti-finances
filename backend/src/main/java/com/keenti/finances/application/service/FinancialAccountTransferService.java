package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.in.FinancialAccountTransferUseCase;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.FinancialAccountTransferRepository;
import com.keenti.finances.domain.port.out.CreditStatementRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class FinancialAccountTransferService implements FinancialAccountTransferUseCase {

    @Inject
    FinancialAccountRepository financialAccountRepository;

    @Inject
    FinancialAccountTransferRepository transferRepository;

    @Inject
    CreditStatementRepository creditStatementRepository;

    @Override
    public List<FinancialAccountTransfer> list() {
        return transferRepository.findAll();
    }

    @Override
    @Transactional
    public FinancialAccountTransfer create(FinancialAccountTransfer transfer) {
        financialAccountRepository.lockTrackingScope();
        if (!financialAccountRepository.isTrackingActive()) {
            throw new ClientErrorException(
                "Activate Financial Account tracking before recording a Transfer", Response.Status.CONFLICT);
        }
        validateTransfer(transfer);
        FinancialAccountTransfer created = transferRepository.save(transfer);
        reallocatePaymentDestination(created.destinationAccountId());
        return created;
    }

    @Override
    @Transactional
    public FinancialAccountTransfer update(Long id, FinancialAccountTransfer transfer) {
        FinancialAccountTransfer existing = transferRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Financial Account Transfer not found: " + id));
        validateTransfer(transfer, existing.sourceAccountId(), existing.destinationAccountId());
        FinancialAccountTransfer updated = transferRepository.update(new FinancialAccountTransfer(id,
            transfer.sourceAccountId(), transfer.destinationAccountId(), transfer.amount(),
            transfer.transferDate(), transfer.notes(), existing.createdAt()));
        reallocatePaymentDestination(existing.destinationAccountId());
        if (!existing.destinationAccountId().equals(updated.destinationAccountId())) {
            reallocatePaymentDestination(updated.destinationAccountId());
        }
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        FinancialAccountTransfer existing = transferRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Financial Account Transfer not found: " + id));
        validateAccountIds(existing.sourceAccountId(), existing.destinationAccountId());
        transferRepository.softDeleteById(id);
        reallocatePaymentDestination(existing.destinationAccountId());
    }

    @Override
    @Transactional
    public void restore(Long id) {
        FinancialAccountTransfer deleted = transferRepository.findDeletedTransferById(id).orElseThrow(() ->
            new NotFoundException("Deleted Financial Account Transfer not found: " + id));
        validateTransfer(deleted);
        transferRepository.restoreById(id);
        FinancialAccountTransfer restored = transferRepository.findById(id).orElseThrow();
        reallocatePaymentDestination(restored.destinationAccountId());
    }

    @Override
    @Transactional
    public void permanentDelete(Long id) {
        FinancialAccountTransfer deleted = transferRepository.findDeletedTransferById(id).orElseThrow(() ->
            new NotFoundException("Deleted Financial Account Transfer not found: " + id));
        validateAccountIds(deleted.sourceAccountId(), deleted.destinationAccountId());
        creditStatementRepository.removeAllocationsForTransfer(id);
        transferRepository.deleteById(id);
    }

    @Override
    public List<TrashItem> listDeleted() {
        return transferRepository.findAllDeleted();
    }

    private void validateTransfer(FinancialAccountTransfer transfer, Long... relatedAccountIds) {
        if (transfer.sourceAccountId() == null || transfer.destinationAccountId() == null
                || transfer.sourceAccountId().equals(transfer.destinationAccountId())) {
            throw new BadRequestException("Transfer source and destination Financial Accounts must be different");
        }
        if (transfer.amount() == null || transfer.amount().compareTo(BigDecimal.ZERO) <= 0
                || transfer.amount().stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Transfer amount must be positive with at most two decimal places");
        }
        if (transfer.transferDate() == null || transfer.transferDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Transfer date is required and cannot be in the future");
        }
        if (transfer.notes() != null && transfer.notes().length() > 500) {
            throw new BadRequestException("Transfer notes must be at most 500 characters");
        }

        validateAccountIds(transfer.sourceAccountId(), transfer.destinationAccountId(), relatedAccountIds);
    }

    private void validateAccountIds(Long firstAccountId, Long secondAccountId, Long... relatedAccountIds) {
        java.util.Set<Long> accountIds = new java.util.TreeSet<>();
        accountIds.add(firstAccountId);
        accountIds.add(secondAccountId);
        for (Long relatedAccountId : relatedAccountIds) {
            if (relatedAccountId != null) {
                accountIds.add(relatedAccountId);
            }
        }
        for (Long accountId : accountIds) {
            var account = financialAccountRepository.lockById(accountId).orElseThrow(() ->
                new NotFoundException("Financial Account not found: " + accountId));
            if (account.isArchived()) {
                throw new ClientErrorException(
                    "Restore an archived Financial Account before changing a Transfer", Response.Status.CONFLICT);
            }
        }
    }

    private void reallocatePaymentDestination(Long accountId) {
        var destination = financialAccountRepository.findById(accountId).orElseThrow();
        if (destination.isCredit()) {
            creditStatementRepository.reallocatePayments(destination.getId());
        }
    }
}
