package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.port.in.PaymentRecordUseCase;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/subscriptions/{subscriptionId}/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentRecordResource {

    @Inject
    PaymentRecordUseCase paymentRecordUseCase;

    @GET
    public Response list(@PathParam("subscriptionId") Long subscriptionId) {
        var body = paymentRecordUseCase.listBySubscription(subscriptionId)
            .stream().map(this::toResponse).toList();
        return Response.ok(body).build();
    }

    @PUT
    @Path("/{paymentId}")
    public Response recordPayment(@PathParam("subscriptionId") Long subscriptionId,
                                  @PathParam("paymentId") Long paymentId) {
        PaymentRecord updated = paymentRecordUseCase.recordPayment(subscriptionId, paymentId);
        return Response.ok(toResponse(updated)).build();
    }

    @PUT
    @Path("/{paymentId}/link-transaction")
    public Response linkTransaction(@PathParam("subscriptionId") Long subscriptionId,
                                    @PathParam("paymentId") Long paymentId,
                                    @Valid LinkTransactionRequest request) {
        PaymentRecord updated = paymentRecordUseCase.linkTransaction(
            subscriptionId, paymentId, request.transactionId());
        return Response.ok(toResponse(updated)).build();
    }

    private PaymentRecordResponse toResponse(PaymentRecord r) {
        return new PaymentRecordResponse(
            r.getId(), r.getSubscriptionId(), r.getMemberId(),
            r.getBillingDate(), r.getAmount(), r.getStatus(),
            r.getPaidDate(), r.getTransactionId(), r.getCreatedAt()
        );
    }
}
