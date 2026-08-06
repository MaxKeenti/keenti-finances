package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.AccountTrackingStatus;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.port.in.FinancialAccountUseCase;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
}
