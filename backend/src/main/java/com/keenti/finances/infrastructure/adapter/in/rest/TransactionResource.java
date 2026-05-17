package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.in.CategoryUseCase;
import com.keenti.finances.domain.port.in.ContactUseCase;
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

@Path("/api/transactions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TransactionResource {

    @Inject
    TransactionUseCase transactionUseCase;

    @Inject
    CategoryUseCase categoryUseCase;

    @Inject
    ContactUseCase contactUseCase;

    @GET
    public Response list() {
        List<TransactionResponse> body = transactionUseCase.list().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Response.ok(body).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return transactionUseCase.getById(id)
                .map(t -> Response.ok(toResponse(t)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Transaction not found\"}")
                        .build());
    }

    @POST
    public Response create(@Valid TransactionRequest request) {
        categoryUseCase.getById(request.categoryId())
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Category not found: " + request.categoryId() + "\"}")
                        .build()));
        if (request.contactId() != null) {
            contactUseCase.getById(request.contactId())
                    .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                            .build()));
        }
        Transaction created = transactionUseCase.create(toTransaction(null, request));
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(created))
                .build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid TransactionRequest request) {
        categoryUseCase.getById(request.categoryId())
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Category not found: " + request.categoryId() + "\"}")
                        .build()));
        if (request.contactId() != null) {
            contactUseCase.getById(request.contactId())
                    .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                            .build()));
        }
        Transaction updated = transactionUseCase.update(id, toTransaction(id, request));
        return Response.ok(toResponse(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        transactionUseCase.delete(id);
        return Response.noContent().build();
    }

    private Transaction toTransaction(Long id, TransactionRequest r) {
        return new Transaction(id, r.amount(), r.direction(), r.description(),
                r.transactionDate(), r.categoryId(), r.contactId());
    }

    private TransactionResponse toResponse(Transaction t) {
        Optional<Category> category = t.getCategoryId() != null
                ? categoryUseCase.getById(t.getCategoryId())
                : Optional.empty();
        String categoryName = category.map(Category::getName).orElse(null);
        String categoryColor = category.map(Category::getColor).orElse(null);
        String contactName = t.getContactId() != null
                ? contactUseCase.getById(t.getContactId()).map(Contact::getName).orElse(null)
                : null;
        return new TransactionResponse(
            t.getId(), t.getAmount(), t.getDirection(), t.getDescription(),
            t.getTransactionDate(), t.getCategoryId(), categoryName, categoryColor,
            t.getContactId(), contactName
        );
    }
}
