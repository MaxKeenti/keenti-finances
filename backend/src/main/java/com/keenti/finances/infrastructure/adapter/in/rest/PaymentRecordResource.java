package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.port.in.PaymentRecordUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/subscriptions/{subscriptionId}/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentRecordResource {

    @Inject
    PaymentRecordUseCase paymentRecordUseCase;

    @GET
    public Response list(@PathParam("subscriptionId") Long subscriptionId) {
        List<PaymentRecordResponse> body = paymentRecordUseCase.listBySubscription(subscriptionId)
            .stream().map(this::toResponse).collect(Collectors.toList());
        return Response.ok(body).build();
    }

    @PUT
    @Path("/{paymentId}")
    public Response recordPayment(@PathParam("subscriptionId") Long subscriptionId,
                                  @PathParam("paymentId") Long paymentId) {
        PaymentRecord updated = paymentRecordUseCase.recordPayment(subscriptionId, paymentId);
        return Response.ok(toResponse(updated)).build();
    }

    private PaymentRecordResponse toResponse(PaymentRecord r) {
        return new PaymentRecordResponse(
            r.getId(), r.getSubscriptionId(), r.getMemberId(),
            r.getBillingDate(), r.getAmount(), r.getStatus(),
            r.getPaidDate(), r.getCreatedAt()
        );
    }
}
