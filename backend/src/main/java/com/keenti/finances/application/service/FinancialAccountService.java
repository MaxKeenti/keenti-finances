package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.AccountTrackingStatus;
import com.keenti.finances.domain.model.Currencies;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.port.in.FinancialAccountUseCase;
import com.keenti.finances.domain.port.out.CreditStatementRepository;
import com.keenti.finances.domain.port.out.CreditMsiPlanRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.PrimaryCurrencyProvider;
import com.keenti.finances.domain.port.out.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FinancialAccountService implements FinancialAccountUseCase {

    private static final Logger LOG = Logger.getLogger(FinancialAccountService.class);
    private static final Set<String> VALID_KINDS = Set.of(
        "CASH", "DEBIT", "CHECKING", "SAVINGS", "CREDIT");

    @Inject
    FinancialAccountRepository financialAccountRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    CreditStatementRepository creditStatementRepository;

    @Inject
    CreditMsiPlanRepository creditMsiPlanRepository;

    @Inject
    PrimaryCurrencyProvider primaryCurrencyProvider;

    @Override
    public AccountTrackingStatus status() {
        Optional<LocalDate> activationDate = financialAccountRepository.getTrackingActivationDate();
        return new AccountTrackingStatus(
            activationDate.isPresent(), financialAccountRepository.isTrackingSetupRequired(),
            activationDate.orElse(null),
            transactionRepository.getNetBalance(Currencies.DEFAULT),
            financialAccountRepository.getTotalBalance(Currencies.DEFAULT));
    }

    @Override
    public List<FinancialAccount> list(boolean archived) {
        return financialAccountRepository.findAll(archived);
    }

    @Override
    public Optional<FinancialAccount> getById(Long id) {
        return financialAccountRepository.findById(id);
    }

    @Override
    @Transactional
    public List<FinancialAccount> activate(LocalDate activationDate, List<FinancialAccount> accounts) {
        if (activationDate == null) {
            throw new BadRequestException("Account tracking requires an activation date");
        }
        if (activationDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("Account tracking cannot be activated in the future");
        }
        financialAccountRepository.lockTrackingScope();
        if (financialAccountRepository.isTrackingActive()) {
            throw new ClientErrorException("Account tracking is already active", Response.Status.CONFLICT);
        }
        if (accounts == null || accounts.isEmpty()) {
            throw new BadRequestException("Account tracking requires at least one Financial Account");
        }

        BigDecimal openingTotal = BigDecimal.ZERO;
        Set<String> normalizedNames = new HashSet<>();
        List<FinancialAccount> resolved = accounts.stream().map(this::withResolvedCurrency).toList();
        for (FinancialAccount account : resolved) {
            validate(account, activationDate);
            if (!normalizedNames.add(account.getName().toLowerCase(java.util.Locale.ROOT))) {
                throw new ClientErrorException("Financial Account names must be unique", Response.Status.CONFLICT);
            }
            // Every legacy Transaction is MXN, so only MXN openings reconcile
            // against them. Other currencies introduce money Keenti never
            // recorded and are unconstrained (ADR-0024).
            if (Currencies.DEFAULT.equals(account.getCurrency())) {
                openingTotal = openingTotal.add(account.getOpeningBalance());
            }
        }

        BigDecimal transactionNetBalance = transactionRepository.getNetBalance(Currencies.DEFAULT);
        if (openingTotal.compareTo(transactionNetBalance) != 0) {
            throw new BadRequestException("MXN Financial Account opening balances must equal the current Net Balance");
        }

        List<FinancialAccount> created = resolved.stream()
            .map(account -> financialAccountRepository.save(account))
            .toList();
        financialAccountRepository.activateTracking(activationDate);
        return created;
    }

    @Override
    @Transactional
    public FinancialAccount create(FinancialAccount requested) {
        FinancialAccount account = withResolvedCurrency(requested);
        if (!financialAccountRepository.isTrackingActive()) {
            throw new ClientErrorException(
                "Activate Financial Account tracking before creating an Account", Response.Status.CONFLICT);
        }
        validate(account, account.getOpeningDate());
        if (financialAccountRepository.existsActiveByName(account.getName())) {
            throw new ClientErrorException("A Financial Account with that name already exists", Response.Status.CONFLICT);
        }
        return financialAccountRepository.save(account);
    }

    @Override
    @Transactional
    public FinancialAccount updateHue(Long id, int hue) {
        financialAccountRepository.lockById(id)
            .orElseThrow(() -> new NotFoundException("Financial Account not found: " + id));
        if (hue < 0 || hue > 359) {
            throw new BadRequestException("Financial Account hue must be between 0 and 359");
        }
        return financialAccountRepository.setHue(id, hue);
    }

    @Override
    @Transactional
    public FinancialAccount archive(Long id) {
        FinancialAccount account = financialAccountRepository.lockById(id)
            .filter(candidate -> !candidate.isArchived())
            .orElseThrow(() -> new NotFoundException("Active Financial Account not found: " + id));
        if (account.getBalance().signum() != 0) {
            throw new BadRequestException("Bring the Financial Account balance to zero before archiving");
        }
        if (account.isCredit() && creditStatementRepository.findByAccountId(id).stream()
                .anyMatch(statement -> statement.officialBalance().subtract(statement.paidAmount()).signum() > 0)) {
            throw new ClientErrorException(
                "Settle every confirmed Credit Statement before archiving this Financial Account",
                Response.Status.CONFLICT);
        }
        if (account.isCredit() && creditMsiPlanRepository.hasActiveByAccountId(id)) {
            throw new ClientErrorException("Finish or cancel every active MSI plan before archiving this Financial Account",
                Response.Status.CONFLICT);
        }
        FinancialAccount archived = financialAccountRepository.setArchived(id, true);
        LOG.infof("financial-account.archive id=%d", id);
        return archived;
    }

    @Override
    @Transactional
    public FinancialAccount restore(Long id) {
        FinancialAccount account = financialAccountRepository.lockById(id)
            .filter(FinancialAccount::isArchived)
            .orElseThrow(() -> new NotFoundException("Archived Financial Account not found: " + id));
        if (financialAccountRepository.existsActiveByName(account.getName())) {
            throw new ClientErrorException(
                "An active Financial Account with that name already exists", Response.Status.CONFLICT);
        }
        FinancialAccount restored = financialAccountRepository.setArchived(id, false);
        LOG.infof("financial-account.restore id=%d", id);
        return restored;
    }

    /** A requested Account without a currency takes the Primary Currency. */
    private FinancialAccount withResolvedCurrency(FinancialAccount account) {
        if (account == null) {
            return null;
        }
        String currency = Currencies.normalize(account.getCurrency());
        if (currency == null) {
            currency = primaryCurrencyProvider.primaryCurrency();
        }
        return new FinancialAccount(account.getId(), account.getName(), account.getKind(), currency,
            account.getHue(), account.getOpeningBalance(), account.getOpeningDate(), account.getBalance(),
            account.isArchived(), account.getCreatedAt(), account.getUpdatedAt(), account.getVersion());
    }

    private void validate(FinancialAccount account, LocalDate expectedOpeningDate) {
        if (account == null || account.getName() == null || account.getName().isBlank()) {
            throw new BadRequestException("Financial Account name is required");
        }
        if (account.getName().trim().length() > 100) {
            throw new BadRequestException("Financial Account name must be at most 100 characters");
        }
        if (!VALID_KINDS.contains(account.getKind())) {
            throw new BadRequestException("Invalid Financial Account kind");
        }
        if (!Currencies.isSupported(account.getCurrency())) {
            throw new BadRequestException("Unsupported currency: " + account.getCurrency());
        }
        if (account.getHue() < 0 || account.getHue() > 359) {
            throw new BadRequestException("Financial Account hue must be between 0 and 359");
        }
        if (account.getOpeningBalance() == null || account.getOpeningBalance().stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Financial Account opening balance supports at most two decimal places");
        }
        if (!expectedOpeningDate.equals(account.getOpeningDate())) {
            throw new BadRequestException("Financial Account opening date must match the tracking date");
        }
    }
}
