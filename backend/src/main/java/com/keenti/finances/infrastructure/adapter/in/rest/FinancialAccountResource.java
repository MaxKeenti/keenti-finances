package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.AccountTrackingStatus;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.model.CreditAccountSettings;
import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.port.in.CreditStatementUseCase;
import com.keenti.finances.domain.port.in.FinancialAccountUseCase;
import com.keenti.finances.domain.port.out.CreditAccountSettingsRepository;
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
        requireCredit(id);
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
        requireCredit(id);
        CreditAccountSettings saved = creditAccountSettingsRepository.save(new CreditAccountSettings(
            id, request.creditLimit(), request.statementClosingDay(), request.paymentDueDay()));
        return Response.ok(toResponse(saved)).build();
    }

    @GET
    @Path("/{id}/credit-statements")
    public Response creditStatements(@PathParam("id") Long id) {
        return Response.ok(creditStatementUseCase.list(id).stream()
            .map(FinancialAccountResource::toResponse).toList()).build();
    }

    @GET
    @Path("/{id}/credit-statements/estimate")
    public Response estimateCreditStatement(@PathParam("id") Long id,
                                            @QueryParam("periodEnd") LocalDate periodEnd) {
        return Response.ok(new CreditStatementEstimateResponse(periodEnd,
            creditStatementUseCase.estimateOutstandingBalance(id, periodEnd))).build();
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
    @Path("/activate")
    public Response activate(@Valid FinancialAccountActivationRequest request) {
        return Response.status(Response.Status.CREATED)
            .entity(financialAccountUseCase.activate(
                request.activationDate(),
                request.accounts().stream()
                    .map(account -> toDomain(account, request.activationDate()))
                    .toList()).stream()
                .map(FinancialAccountResource::toResponse)
                .toList())
            .build();
    }

    @POST
    public Response create(@Valid FinancialAccountRequest request) {
        FinancialAccount created = financialAccountUseCase.create(toDomain(request, LocalDate.now()));
        return Response.status(Response.Status.CREATED).entity(toResponse(created)).build();
    }

    private static FinancialAccount toDomain(FinancialAccountRequest request, LocalDate openingDate) {
        return new FinancialAccount(
            null, request.name().trim(), request.kind().trim().toUpperCase(java.util.Locale.ROOT),
            request.openingBalance(), openingDate, BigDecimal.ZERO, false, null, null, 0);
    }

    private static FinancialAccountResponse toResponse(FinancialAccount account) {
        return new FinancialAccountResponse(
            account.getId(), account.getName(), account.getKind(), account.getOpeningBalance(),
            account.getOpeningDate(), account.getBalance(), account.isArchived(),
            account.getCreatedAt(), account.getUpdatedAt(), account.getVersion());
    }

    private static AccountTrackingStatusResponse toResponse(AccountTrackingStatus status) {
        return new AccountTrackingStatusResponse(status.active(), status.activatedAt(),
            status.transactionNetBalance(), status.accountNetBalance());
    }

    private void requireCredit(Long id) {
        FinancialAccount account = financialAccountUseCase.getById(id).orElseThrow(() ->
            new jakarta.ws.rs.NotFoundException("Financial Account not found: " + id));
        if (!account.isCredit()) {
            throw new jakarta.ws.rs.BadRequestException("Credit settings require a CREDIT Financial Account");
        }
    }

    private static CreditAccountSettingsResponse toResponse(CreditAccountSettings settings) {
        return new CreditAccountSettingsResponse(settings.accountId(), settings.creditLimit(),
            settings.statementClosingDay(), settings.paymentDueDay());
    }

    private static CreditStatementResponse toResponse(CreditStatement statement) {
        return new CreditStatementResponse(statement.id(), statement.accountId(), statement.periodStart(),
            statement.periodEnd(), statement.dueDate(), statement.estimatedBalance(),
            statement.officialBalance(), statement.officialMinimumPayment(),
            statement.officialAvoidInterest(), statement.officialNote(), statement.confirmedAt(),
            statement.paidAmount(), statement.officialBalance().subtract(statement.paidAmount()));
    }
}
