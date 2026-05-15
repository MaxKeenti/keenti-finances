package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.application.service.DebtService;
import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.DebtPayment;
import com.keenti.finances.domain.port.in.ContactUseCase;
import com.keenti.finances.domain.port.in.DebtUseCase;
import com.keenti.finances.domain.port.in.DebtUseCase.BulkPaymentResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/debts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DebtResource {

    @Inject
    DebtUseCase debtUseCase;

    @Inject
    DebtService debtService;

    @Inject
    ContactUseCase contactUseCase;

    @GET
    public Response list() {
        List<DebtResponse> body = debtUseCase.list().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Response.ok(body).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return debtUseCase.getById(id)
                .map(d -> Response.ok(toResponse(d)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Debt not found\"}")
                        .build());
    }

    @POST
    public Response create(@Valid DebtRequest request) {
        contactUseCase.getById(request.contactId())
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                        .build()));
        LocalDateTime createdAtTs = request.createdAt() != null
            ? request.createdAt().atStartOfDay()
            : null;
        Debt created = debtUseCase.create(new Debt(
            null, request.contactId(), request.description(), request.totalAmount(), "ACTIVE", createdAtTs));
        return Response.status(Response.Status.CREATED).entity(toResponse(created)).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid DebtRequest request) {
        contactUseCase.getById(request.contactId())
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                        .build()));
        Debt debt = debtUseCase.getById(id).orElseThrow(() ->
            new jakarta.ws.rs.NotFoundException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Debt not found: " + id + "\"}")
                    .build()));
        LocalDateTime updatedCreatedAt = request.createdAt() != null
            ? request.createdAt().atStartOfDay()
            : debt.getCreatedAt();
        Debt updated = debtUseCase.update(id, new Debt(
            id, request.contactId(), request.description(), request.totalAmount(),
            debt.getStatus(), updatedCreatedAt));
        return Response.ok(toResponse(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        debtUseCase.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/payments")
    public Response recordPayment(@PathParam("id") Long id, @Valid DebtPaymentRequest request) {
        DebtPayment payment = debtUseCase.recordPayment(
            id, request.amount(), request.paymentDate(), request.categoryId(), request.notes());
        return Response.status(Response.Status.CREATED).entity(toPaymentResponse(payment)).build();
    }

    @GET
    @Path("/{id}/payments")
    public Response listPayments(@PathParam("id") Long id) {
        List<DebtPaymentResponse> body = debtUseCase.listPayments(id).stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList());
        return Response.ok(body).build();
    }

    @POST
    @Path("/bulk-payment")
    public Response bulkPayment(@Valid BulkPaymentRequest request) {
        contactUseCase.getById(request.contactId())
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                        .build()));

        BulkPaymentResult result = debtUseCase.bulkPayment(
            request.contactId(), request.totalAmount(),
            request.paymentDate(), request.categoryId(), request.notes());

        String contactName = contactUseCase.getById(request.contactId())
                .map(Contact::getName).orElse(null);

        BulkPaymentResponse response = new BulkPaymentResponse(
            result.contactId(),
            contactName,
            result.totalAmount(),
            result.totalApplied(),
            result.totalUnused(),
            result.payments().stream()
                .map(p -> new BulkPaymentItemResponse(
                    p.debtId(), p.description(), p.applied(), p.remaining(), p.debtStatus()))
                .collect(Collectors.toList()));

        return Response.ok(response).build();
    }

    private DebtResponse toResponse(Debt d) {
        String contactName = d.getContactId() != null
                ? contactUseCase.getById(d.getContactId()).map(Contact::getName).orElse(null)
                : null;
        BigDecimal totalPaid = debtService.getRemainingBalance(d.getId()) != null
                ? d.getTotalAmount().subtract(debtService.getRemainingBalance(d.getId()))
                : BigDecimal.ZERO;
        BigDecimal remaining = debtService.getRemainingBalance(d.getId());
        return new DebtResponse(
            d.getId(), d.getContactId(), contactName, d.getDescription(),
            d.getTotalAmount(), totalPaid, remaining, d.getStatus(), d.getCreatedAt());
    }

    private DebtPaymentResponse toPaymentResponse(DebtPayment p) {
        return new DebtPaymentResponse(
            p.getId(), p.getDebtId(), p.getAmount(), p.getPaymentDate(),
            p.getTransactionId(), p.getNotes(), p.getCreatedAt());
    }
}
