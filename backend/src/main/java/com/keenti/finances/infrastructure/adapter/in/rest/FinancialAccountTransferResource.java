package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import com.keenti.finances.domain.port.in.FinancialAccountTransferUseCase;
import com.keenti.finances.domain.port.in.FinancialAccountUseCase;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;

@Path("/api/account-transfers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FinancialAccountTransferResource {

    @Inject
    FinancialAccountTransferUseCase transferUseCase;

    @Inject
    FinancialAccountUseCase financialAccountUseCase;

    @GET
    public Response list() {
        return Response.ok(transferUseCase.list().stream()
            .map(this::toResponse)
            .toList()).build();
    }

    @POST
    public Response create(@Valid FinancialAccountTransferRequest request) {
        FinancialAccountTransfer created = transferUseCase.create(new FinancialAccountTransfer(
            null, request.sourceAccountId(), request.destinationAccountId(), request.amount(),
            request.transferDate(), request.notes(), null));
        return Response.status(Response.Status.CREATED).entity(toResponse(created)).build();
    }

    private FinancialAccountTransferResponse toResponse(FinancialAccountTransfer transfer) {
        Optional<String> sourceName = financialAccountUseCase.getById(transfer.sourceAccountId())
            .map(account -> account.getName());
        Optional<String> destinationName = financialAccountUseCase.getById(transfer.destinationAccountId())
            .map(account -> account.getName());
        return new FinancialAccountTransferResponse(transfer.id(), transfer.sourceAccountId(),
            sourceName.orElse(null), transfer.destinationAccountId(), destinationName.orElse(null),
            transfer.amount(), transfer.transferDate(), transfer.notes(), transfer.createdAt());
    }
}
