package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.port.in.CreditStatementUseCase;
import com.keenti.finances.domain.port.out.CreditStatementRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class CreditStatementService implements CreditStatementUseCase {

    @Inject FinancialAccountRepository financialAccountRepository;
    @Inject CreditStatementRepository creditStatementRepository;

    @Override
    public List<CreditStatement> list(Long accountId) {
        requireCreditAccount(accountId);
        return creditStatementRepository.findByAccountId(accountId);
    }

    @Override
    public BigDecimal estimateOutstandingBalance(Long accountId, LocalDate periodEnd) {
        requireCreditAccount(accountId);
        if (periodEnd == null) {
            throw new BadRequestException("Statement period end is required");
        }
        BigDecimal signedBalance = financialAccountRepository.getBalanceAt(accountId, periodEnd);
        return signedBalance.signum() < 0 ? signedBalance.negate() : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public CreditStatement confirm(CreditStatement statement) {
        requireCreditAccount(statement.accountId());
        validate(statement);
        if (creditStatementRepository.findByAccountIdAndPeriod(
                statement.accountId(), statement.periodStart(), statement.periodEnd()).isPresent()) {
            throw new ClientErrorException("This statement period is already confirmed",
                Response.Status.CONFLICT);
        }
        BigDecimal estimate = estimateOutstandingBalance(statement.accountId(), statement.periodEnd());
        return creditStatementRepository.save(new CreditStatement(null, statement.accountId(),
            statement.periodStart(), statement.periodEnd(), statement.dueDate(), estimate,
            statement.officialBalance(), statement.officialMinimumPayment(),
            statement.officialAvoidInterest(), statement.officialNote(), LocalDateTime.now(), BigDecimal.ZERO));
    }

    @Override
    @Transactional
    public CreditStatement reconfirm(Long statementId, CreditStatement statement) {
        CreditStatement existing = creditStatementRepository.findById(statementId).orElseThrow(() ->
            new NotFoundException("Credit statement not found: " + statementId));
        if (!existing.accountId().equals(statement.accountId())
                || !existing.periodStart().equals(statement.periodStart())
                || !existing.periodEnd().equals(statement.periodEnd())) {
            throw new BadRequestException("A reconfirmation cannot change the statement period or Financial Account");
        }
        validate(statement);
        return creditStatementRepository.updateOfficialFigures(new CreditStatement(statementId,
            existing.accountId(), existing.periodStart(), existing.periodEnd(), statement.dueDate(),
            existing.estimatedBalance(), statement.officialBalance(), statement.officialMinimumPayment(),
            statement.officialAvoidInterest(), statement.officialNote(), existing.confirmedAt(), existing.paidAmount()));
    }

    private void requireCreditAccount(Long accountId) {
        if (!financialAccountRepository.isTrackingActive()) {
            throw new ClientErrorException("Activate Financial Account tracking before managing statements",
                Response.Status.CONFLICT);
        }
        var account = financialAccountRepository.findById(accountId).orElseThrow(() ->
            new NotFoundException("Financial Account not found: " + accountId));
        if (!account.isCredit()) {
            throw new BadRequestException("Credit statements require a CREDIT Financial Account");
        }
    }

    private void validate(CreditStatement statement) {
        if (statement.periodStart() == null || statement.periodEnd() == null || statement.dueDate() == null) {
            throw new BadRequestException("Statement period and due date are required");
        }
        if (statement.periodEnd().isBefore(statement.periodStart())) {
            throw new BadRequestException("Statement period end cannot be before the start");
        }
        if (statement.dueDate().isBefore(statement.periodEnd())) {
            throw new BadRequestException("Statement due date cannot be before the period end");
        }
        validateMoney(statement.officialBalance(), "Official statement balance");
        validateMoney(statement.officialMinimumPayment(), "Official minimum payment");
        validateMoney(statement.officialAvoidInterest(), "Payment to avoid interest");
        if (statement.officialMinimumPayment().compareTo(statement.officialBalance()) > 0
                || statement.officialAvoidInterest().compareTo(statement.officialBalance()) > 0) {
            throw new BadRequestException("Official payments cannot exceed the statement balance");
        }
        if (statement.officialNote() != null && statement.officialNote().length() > 500) {
            throw new BadRequestException("Official statement note must be at most 500 characters");
        }
    }

    private void validateMoney(BigDecimal amount, String name) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException(name + " must be non-negative with at most two decimal places");
        }
    }
}
