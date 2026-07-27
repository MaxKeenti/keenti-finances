package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PublicSubscriptionView;
import com.keenti.finances.domain.port.in.PublicSubscriptionViewUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/api/public/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
public class PublicSubscriptionResource {

    private static final Logger LOG = Logger.getLogger(PublicSubscriptionResource.class);

    @Inject
    PublicSubscriptionViewUseCase publicSubscriptionViewUseCase;

    @GET
    @Path("/{token}")
    @SuppressWarnings("null")
    public Response getByToken(@PathParam("token") String token) {
        var result = publicSubscriptionViewUseCase.getByToken(token);
        if (result.isEmpty()) {
            LOG.info("public.subscription.lookup result=not_found status=404");
            return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Subscription not found\"}")
                .build();
        }

        PublicSubscriptionView view = result.get();
        LOG.infof(
            "public.subscription.lookup result=found status=200 subscriptionId=%d",
            view.subscriptionId()
        );
        return Response.ok(toResponse(view)).build();
    }

    private PublicSubscriptionResponse toResponse(PublicSubscriptionView view) {
        var members = view.members().stream()
            .map(member -> new PublicSubscriptionResponse.MemberPaymentSummary(
                member.memberId(),
                member.contactId(),
                member.contactName(),
                member.shareAmount(),
                member.payments().stream()
                    .map(payment -> new PublicSubscriptionResponse.PaymentSummary(
                        payment.paymentId(),
                        payment.billingDate(),
                        payment.amount(),
                        payment.status(),
                        payment.paidDate()
                    ))
                    .toList()
            ))
            .toList();
        return new PublicSubscriptionResponse(
            view.subscriptionName(),
            view.cost(),
            view.billingCycle(),
            view.nextBillingDate(),
            members
        );
    }
}
