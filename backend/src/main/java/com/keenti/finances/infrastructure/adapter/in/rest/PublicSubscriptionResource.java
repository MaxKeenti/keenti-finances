package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.SubscriptionMember;
import com.keenti.finances.domain.port.in.ContactUseCase;
import com.keenti.finances.domain.port.in.PaymentRecordUseCase;
import com.keenti.finances.domain.port.in.SubscriptionUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

@Path("/api/public/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
public class PublicSubscriptionResource {

    private static final Logger LOG = Logger.getLogger(PublicSubscriptionResource.class);

    @Inject
    SubscriptionUseCase subscriptionUseCase;

    @Inject
    ContactUseCase contactUseCase;

    @Inject
    PaymentRecordUseCase paymentRecordUseCase;

    @GET
    @Path("/{token}")
    public Response getByToken(@PathParam("token") String token) {
        var result = subscriptionUseCase.getByToken(token);
        if (result.isEmpty()) {
            LOG.infof("public.subscription.token.notfound token=%s", token);
            return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Subscription not found\"}")
                .build();
        }

        Subscription sub = result.get();
        LOG.infof("public.subscription.token.found token=%s subscriptionId=%d", token, sub.getId());

        List<SubscriptionMember> members = subscriptionUseCase.listMembers(sub.getId());
        List<PaymentRecord> allPayments = paymentRecordUseCase.listBySubscription(sub.getId());

        List<PublicSubscriptionResponse.MemberPaymentSummary> memberSummaries = members.stream()
            .map(m -> {
                String contactName = contactUseCase.getById(m.getContactId())
                    .map(c -> c.getName())
                    .orElse(null);

                List<PublicSubscriptionResponse.PaymentSummary> payments = allPayments.stream()
                    .filter(p -> m.getId().equals(p.getMemberId()))
                    .map(p -> new PublicSubscriptionResponse.PaymentSummary(
                        p.getId(), p.getBillingDate(), p.getAmount(), p.getStatus(), p.getPaidDate()
                    ))
                    .collect(Collectors.toList());

                return new PublicSubscriptionResponse.MemberPaymentSummary(
                    m.getId(), m.getContactId(), contactName, m.getShareAmount(), payments
                );
            })
            .collect(Collectors.toList());

        PublicSubscriptionResponse response = new PublicSubscriptionResponse(
            sub.getName(), sub.getCost(), sub.getBillingCycle(), sub.getNextBillingDate(), memberSummaries
        );

        return Response.ok(response).build();
    }
}
