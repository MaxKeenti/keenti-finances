package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.SubscriptionMember;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.in.CategoryUseCase;
import com.keenti.finances.domain.port.in.ContactUseCase;
import com.keenti.finances.domain.port.in.SubscriptionUseCase;
import com.keenti.finances.domain.port.in.TransactionUseCase;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/api/subscriptions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionResource {

    @Inject
    SubscriptionUseCase subscriptionUseCase;

    @Inject
    ContactUseCase contactUseCase;

    @Inject
    CategoryUseCase categoryUseCase;

    @Inject
    TransactionUseCase transactionUseCase;

    @GET
    public Response list() {
        List<SubscriptionResponse> body = subscriptionUseCase.list().stream()
            .map(this::toResponse).collect(Collectors.toList());
        return Response.ok(body).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return subscriptionUseCase.getById(id)
            .map(s -> Response.ok(toResponse(s)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Subscription not found\"}")
                .build());
    }

    @POST
    public Response create(@Valid SubscriptionRequest request) {
        Subscription created = subscriptionUseCase.create(toSubscription(null, request));
        return Response.status(Response.Status.CREATED).entity(toResponse(created)).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid SubscriptionRequest request) {
        Subscription updated = subscriptionUseCase.update(id, toSubscription(id, request));
        return Response.ok(toResponse(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        subscriptionUseCase.delete(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/members")
    public Response listMembers(@PathParam("id") Long id) {
        List<MemberResponse> members = subscriptionUseCase.listMembers(id).stream()
            .map(this::toMemberResponse).collect(Collectors.toList());
        return Response.ok(members).build();
    }

    @POST
    @Path("/{id}/members")
    public Response addMember(@PathParam("id") Long id, @Valid MemberRequest request) {
        contactUseCase.getById(request.contactId())
            .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                    .build()));
        SubscriptionMember member = subscriptionUseCase.addMember(id, request.contactId());
        return Response.status(Response.Status.CREATED).entity(toMemberResponse(member)).build();
    }

    @DELETE
    @Path("/{id}/members/{memberId}")
    public Response removeMember(@PathParam("id") Long id, @PathParam("memberId") Long memberId) {
        subscriptionUseCase.removeMember(id, memberId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/linked-transactions")
    public Response listLinkedTransactions(@PathParam("id") Long id) {
        List<TransactionResponse> body = transactionUseCase.listBySubscriptionId(id).stream()
            .map(this::toTransactionResponse)
            .collect(Collectors.toList());
        return Response.ok(body).build();
    }

    private Subscription toSubscription(Long id, SubscriptionRequest r) {
        return new Subscription(id, r.name(), r.cost(), r.billingCycle(), r.type(),
            r.categoryId(), r.nextBillingDate(), null, null, r.ownerParticipatesOrDefault());
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(
            s.getId(), s.getName(), s.getCost(), s.getBillingCycle(), s.getType(),
            s.getCategoryId(), s.getNextBillingDate(), s.getTokenUuid(), s.getCreatedAt(),
            s.isOwnerParticipates()
        );
    }

    private TransactionResponse toTransactionResponse(Transaction t) {
        Optional<Category> category = t.getCategoryId() != null
            ? categoryUseCase.getById(t.getCategoryId())
            : Optional.empty();
        String categoryName = category.map(Category::getName).orElse(null);
        Integer categoryHue = category.map(Category::getHue).orElse(null);
        String contactName = t.getContactId() != null
            ? contactUseCase.getById(t.getContactId()).map(Contact::getName).orElse(null)
            : null;
        return new TransactionResponse(
            t.getId(), t.getAmount(), t.getDirection(), t.getDescription(),
            t.getTransactionDate(), t.getCategoryId(), categoryName, categoryHue,
            t.getContactId(), contactName, t.getSubscriptionId()
        );
    }

    private MemberResponse toMemberResponse(SubscriptionMember m) {
        String contactName = m.getContactId() != null
            ? contactUseCase.getById(m.getContactId()).map(Contact::getName).orElse(null)
            : null;
        return new MemberResponse(
            m.getId(), m.getSubscriptionId(), m.getContactId(),
            contactName, m.getShareAmount(), m.getCreatedAt()
        );
    }
}
