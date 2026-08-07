package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.AccountTrackingStatus;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.model.CreditAccountSettings;
import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.port.in.CreditStatementUseCase;
import com.keenti.finances.domain.port.in.FinancialAccountUseCase;
import com.keenti.finances.domain.port.out.CreditAccountSettingsRepository;
import com.keenti.finances.domain.port.out.CreditStatementRepository;
import com.keenti.finances.domain.port.out.CreditMsiPlanRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.TransactionRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import com.keenti.finances.domain.model.CreditMsiPlan;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;

@Path("/api/accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FinancialAccountResource {

    @Inject
    FinancialAccountUseCase financialAccountUseCase;

    @Inject
    CreditAccountSettingsRepository creditAccountSettingsRepository;

    @Inject
    CreditStatementUseCase creditStatementUseCase;

    @Inject CreditMsiPlanRepository creditMsiPlanRepository;
    @Inject FinancialAccountRepository financialAccountRepository;
    @Inject TransactionRepository transactionRepository;
    @Inject CreditStatementRepository creditStatementRepository;
    @Inject UserTimeZoneProvider userTimeZoneProvider;

    @GET
    public Response list(@QueryParam("archived") @DefaultValue("false") boolean archived) {
        return Response.ok(financialAccountUseCase.list(archived).stream()
            .map(FinancialAccountResource::toResponse)
            .toList()).build();
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(toResponse(financialAccountUseCase.status())).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return financialAccountUseCase.getById(id)
            .map(account -> Response.ok(toResponse(account)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Financial Account not found\"}").build());
    }

    @GET
    @Path("/{id}/credit-settings")
    public Response creditSettings(@PathParam("id") Long id) {
        requireCredit(id, false);
        return creditAccountSettingsRepository.findByAccountId(id)
            .map(settings -> Response.ok(toResponse(settings)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Credit settings not configured\"}").build());
    }

    @PUT
    @Path("/{id}/credit-settings")
    @Transactional
    public Response saveCreditSettings(@PathParam("id") Long id,
                                       @Valid CreditAccountSettingsRequest request) {
        requireCredit(id, true);
        CreditAccountSettings saved = creditAccountSettingsRepository.save(new CreditAccountSettings(
            id, request.creditLimit(), request.statementClosingDay(), request.paymentDueDay()));
        return Response.ok(toResponse(saved)).build();
    }

    @GET
    @Path("/{id}/credit-statements")
    public Response creditStatements(@PathParam("id") Long id) {
        return Response.ok(creditStatementUseCase.list(id).stream()
            .map(this::toResponse).toList()).build();
    }

    @GET
    @Path("/{id}/credit-statements/estimate")
    public Response estimateCreditStatement(@PathParam("id") Long id,
                                            @QueryParam("periodEnd") LocalDate periodEnd) {
        BigDecimal balance = creditStatementUseCase.estimateOutstandingBalance(id, periodEnd);
        return Response.ok(new CreditStatementEstimateResponse(null, periodEnd, null, balance)).build();
    }

    @GET
    @Path("/{id}/credit-statements/current-estimate")
    public Response currentCreditStatementEstimate(@PathParam("id") Long id) {
        var estimate = creditStatementUseCase.estimateCurrentStatement(id, userTimeZoneProvider.today());
        return Response.ok(new CreditStatementEstimateResponse(estimate.periodStart(), estimate.periodEnd(),
            estimate.dueDate(), estimate.estimatedBalance())).build();
    }

    @POST
    @Path("/{id}/credit-statements")
    public Response confirmCreditStatement(@PathParam("id") Long id,
                                           @Valid CreditStatementRequest request) {
        CreditStatement confirmed = creditStatementUseCase.confirm(new CreditStatement(null, id,
            request.periodStart(), request.periodEnd(), request.dueDate(), null,
            request.officialBalance(), request.officialMinimumPayment(),
            request.officialAvoidInterest(), request.officialNote(), null, null));
        return Response.status(Response.Status.CREATED).entity(toResponse(confirmed)).build();
    }

    @POST
    @Path("/{id}/credit-statements/{statementId}/reconfirm")
    public Response reconfirmCreditStatement(@PathParam("id") Long id, @PathParam("statementId") Long statementId,
                                             @Valid CreditStatementRequest request) {
        CreditStatement reconfirmed = creditStatementUseCase.reconfirm(statementId, new CreditStatement(null, id,
            request.periodStart(), request.periodEnd(), request.dueDate(), null,
            request.officialBalance(), request.officialMinimumPayment(),
            request.officialAvoidInterest(), request.officialNote(), null, null));
        return Response.ok(toResponse(reconfirmed)).build();
    }

    @GET
    @Path("/{id}/msi-plans")
    public Response msiPlans(@PathParam("id") Long id) {
        requireCredit(id, false);
        return Response.ok(creditMsiPlanRepository.findByAccountId(id).stream().map(FinancialAccountResource::toResponse).toList()).build();
    }

    @POST
    @Path("/{id}/msi-plans")
    @Transactional
    public Response createMsiPlan(@PathParam("id") Long id, @Valid CreditMsiPlanRequest request) {
        requireCredit(id, true);
        var transaction = transactionRepository.findById(request.transactionId()).orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Transaction not found"));
        if (!idEquals(transaction.getAccountId(), id) || !"EGRESS".equals(transaction.getDirection())) throw new jakarta.ws.rs.BadRequestException("An MSI plan requires an EGRESS Transaction on this Credit Account");
        if (creditMsiPlanRepository.existsByTransactionId(request.transactionId())) throw new jakarta.ws.rs.ClientErrorException("This purchase already has an MSI plan", Response.Status.CONFLICT);
        if (request.firstInstallmentDate().isBefore(transaction.getTransactionDate())) throw new jakarta.ws.rs.BadRequestException("The first installment cannot precede the purchase");
        BigDecimal divisor = BigDecimal.valueOf(request.installmentCount());
        try { transaction.getAmount().divide(divisor); } catch (ArithmeticException e) { throw new jakarta.ws.rs.BadRequestException("The purchase amount must divide exactly into MXN-cent installments"); }
        CreditMsiPlan saved = creditMsiPlanRepository.save(new CreditMsiPlan(null, id, request.transactionId(),
            transaction.getAmount(), request.installmentCount(), request.firstInstallmentDate(),
            BigDecimal.ZERO, null, null));
        return Response.status(Response.Status.CREATED).entity(toResponse(saved)).build();
    }

    @POST
    @Path("/{id}/msi-plans/{planId}/end")
    @Transactional
    public Response endMsiPlan(@PathParam("id") Long id, @PathParam("planId") Long planId,
                               @Valid CreditMsiPlanEndRequest request) {
        requireCredit(id, true);
        CreditMsiPlan plan = creditMsiPlanRepository.findById(planId).orElseThrow(() ->
            new jakarta.ws.rs.NotFoundException("MSI plan not found"));
        if (!plan.accountId().equals(id)) {
            throw new jakarta.ws.rs.NotFoundException("MSI plan not found");
        }
        if (!plan.active()) {
            throw new jakarta.ws.rs.ClientErrorException("This MSI plan has already ended", Response.Status.CONFLICT);
        }
        String reason = request.reason().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"COMPLETED".equals(reason) && !"CANCELLED".equals(reason)) {
            throw new jakarta.ws.rs.BadRequestException("MSI plan end reason must be COMPLETED or CANCELLED");
        }
        return Response.ok(toResponse(creditMsiPlanRepository.end(planId, java.time.LocalDateTime.now(), reason))).build();
    }

    @POST
    @Path("/activate")
    @Transactional
    public Response activate(@Valid FinancialAccountActivationRequest request) {
        validateOpeningImports(request);
        var accounts = financialAccountUseCase.activate(
            request.activationDate(),
            request.accounts().stream()
                .map(account -> toDomain(account, request.activationDate()))
                .toList());
        for (int index = 0; index < accounts.size(); index++) {
            importOpeningCreditMetadata(accounts.get(index), request.accounts().get(index));
        }
        return Response.status(Response.Status.CREATED)
            .entity(accounts.stream().map(FinancialAccountResource::toResponse).toList()).build();
    }

    @POST
    public Response create(@Valid FinancialAccountRequest request) {
        FinancialAccount created = financialAccountUseCase.create(toDomain(request, LocalDate.now()));
        return Response.status(Response.Status.CREATED).entity(toResponse(created)).build();
    }

    @PUT
    @Path("/{id}/appearance")
    public Response updateAppearance(@PathParam("id") Long id,
                                     @Valid FinancialAccountAppearanceRequest request) {
        return Response.ok(toResponse(financialAccountUseCase.updateHue(id, request.hue()))).build();
    }

    @POST
    @Path("/{id}/archive")
    @Consumes(MediaType.WILDCARD)
    public Response archive(@PathParam("id") Long id) {
        return Response.ok(toResponse(financialAccountUseCase.archive(id))).build();
    }

    @POST
    @Path("/{id}/restore")
    @Consumes(MediaType.WILDCARD)
    public Response restore(@PathParam("id") Long id) {
        return Response.ok(toResponse(financialAccountUseCase.restore(id))).build();
    }

    private static FinancialAccount toDomain(FinancialAccountRequest request, LocalDate openingDate) {
        return new FinancialAccount(
            null, request.name().trim(), request.kind().trim().toUpperCase(java.util.Locale.ROOT), request.hue(),
            request.openingBalance(), openingDate, BigDecimal.ZERO, false, null, null, 0);
    }

    private void validateOpeningImports(FinancialAccountActivationRequest request) {
        for (FinancialAccountRequest account : request.accounts()) {
            boolean hasCreditMetadata = account.creditSettings() != null
                || (account.openingCreditStatements() != null && !account.openingCreditStatements().isEmpty())
                || (account.openingMsiPlans() != null && !account.openingMsiPlans().isEmpty());
            if (hasCreditMetadata && !"CREDIT".equalsIgnoreCase(account.kind())) {
                throw new jakarta.ws.rs.BadRequestException(
                    "Opening Credit metadata requires a CREDIT Financial Account");
            }
            if (account.openingMsiPlans() != null) {
                for (OpeningCreditMsiPlanRequest plan : account.openingMsiPlans()) {
                    if (plan.remainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new jakarta.ws.rs.BadRequestException("Opening MSI amount must be positive");
                    }
                    try {
                        plan.remainingAmount().divide(BigDecimal.valueOf(plan.remainingInstallmentCount()));
                    } catch (ArithmeticException e) {
                        throw new jakarta.ws.rs.BadRequestException(
                            "Opening MSI amount must divide exactly into MXN-cent installments");
                    }
                }
            }
        }
    }

    private void importOpeningCreditMetadata(FinancialAccount account, FinancialAccountRequest request) {
        if (!account.isCredit()) {
            return;
        }
        if (request.creditSettings() != null) {
            creditAccountSettingsRepository.save(new CreditAccountSettings(account.getId(),
                request.creditSettings().creditLimit(), request.creditSettings().statementClosingDay(),
                request.creditSettings().paymentDueDay()));
        }
        if (request.openingCreditStatements() != null) {
            for (OpeningCreditStatementRequest statement : request.openingCreditStatements()) {
                if (statement.periodEnd().isBefore(statement.periodStart())
                        || statement.dueDate().isBefore(statement.periodEnd())
                        || statement.officialBalance().compareTo(BigDecimal.ZERO) < 0
                        || statement.officialMinimumPayment().compareTo(BigDecimal.ZERO) < 0
                        || statement.officialAvoidInterest().compareTo(BigDecimal.ZERO) < 0
                        || statement.officialMinimumPayment().compareTo(statement.officialBalance()) > 0
                        || statement.officialAvoidInterest().compareTo(statement.officialBalance()) > 0) {
                    throw new jakarta.ws.rs.BadRequestException("Invalid opening Credit Statement");
                }
                creditStatementRepository.save(new CreditStatement(null, account.getId(),
                    statement.periodStart(), statement.periodEnd(), statement.dueDate(),
                    statement.officialBalance(), statement.officialBalance(),
                    statement.officialMinimumPayment(), statement.officialAvoidInterest(),
                    statement.officialNote(), java.time.LocalDateTime.now(), BigDecimal.ZERO));
            }
        }
        if (request.openingMsiPlans() != null) {
            for (OpeningCreditMsiPlanRequest plan : request.openingMsiPlans()) {
                creditMsiPlanRepository.save(new CreditMsiPlan(null, account.getId(), null,
                    plan.remainingAmount(), plan.remainingInstallmentCount(), plan.firstInstallmentDate(),
                    plan.remainingAmount(), null, null));
            }
        }
        creditStatementRepository.reallocatePayments(account.getId());
    }

    private static FinancialAccountResponse toResponse(FinancialAccount account) {
        return new FinancialAccountResponse(
            account.getId(), account.getName(), account.getKind(), account.getHue(), account.getOpeningBalance(),
            account.getOpeningDate(), account.getBalance(), account.isArchived(),
            account.getCreatedAt(), account.getUpdatedAt(), account.getVersion());
    }

    private static AccountTrackingStatusResponse toResponse(AccountTrackingStatus status) {
        return new AccountTrackingStatusResponse(status.active(), status.setupRequired(), status.activatedAt(),
            status.transactionNetBalance(), status.accountNetBalance());
    }

    private FinancialAccount requireCredit(Long id, boolean requireActive) {
        FinancialAccount account = (requireActive
                ? financialAccountRepository.lockById(id)
                : financialAccountUseCase.getById(id)).orElseThrow(() ->
            new jakarta.ws.rs.NotFoundException("Financial Account not found: " + id));
        if (!account.isCredit()) {
            throw new jakarta.ws.rs.BadRequestException("Credit settings require a CREDIT Financial Account");
        }
        if (requireActive && account.isArchived()) {
            throw new jakarta.ws.rs.ClientErrorException(
                "Restore the Financial Account before changing Credit settings or MSI plans",
                Response.Status.CONFLICT);
        }
        return account;
    }

    private static CreditAccountSettingsResponse toResponse(CreditAccountSettings settings) {
        return new CreditAccountSettingsResponse(settings.accountId(), settings.creditLimit(),
            settings.statementClosingDay(), settings.paymentDueDay());
    }

    private CreditStatementResponse toResponse(CreditStatement statement) {
        BigDecimal currentEstimate = creditStatementUseCase.estimateOutstandingBalance(
            statement.accountId(), statement.periodEnd());
        BigDecimal mismatchAmount = currentEstimate.subtract(statement.estimatedBalance());
        return new CreditStatementResponse(statement.id(), statement.accountId(), statement.periodStart(),
            statement.periodEnd(), statement.dueDate(), statement.estimatedBalance(),
            statement.officialBalance(), statement.officialMinimumPayment(),
            statement.officialAvoidInterest(), statement.officialNote(), statement.confirmedAt(),
            statement.paidAmount(), statement.officialBalance().subtract(statement.paidAmount()),
            mismatchAmount.signum() != 0, mismatchAmount,
            creditStatementRepository.revisionCount(statement.id()));
    }

    private static CreditMsiPlanResponse toResponse(CreditMsiPlan plan) { return new CreditMsiPlanResponse(plan.id(), plan.transactionId(), plan.purchaseAmount(), plan.installmentCount(), plan.installmentAmount(), plan.firstInstallmentDate(), plan.active(), plan.endedAt(), plan.endReason()); }
    private static boolean idEquals(Long left, Long right) { return left != null && left.equals(right); }
}
