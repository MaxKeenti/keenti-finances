package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.in.CategoryUseCase;
import com.keenti.finances.domain.port.in.ContactUseCase;
import com.keenti.finances.domain.port.in.DebtUseCase;
import com.keenti.finances.domain.port.in.SubscriptionUseCase;
import com.keenti.finances.domain.port.in.TransactionUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/trash")
@Produces(MediaType.APPLICATION_JSON)
public class TrashResource {

    private static final Set<String> VALID_TYPES = Set.of("transaction", "category", "contact", "subscription", "debt");

    @Inject CategoryUseCase categoryUseCase;
    @Inject ContactUseCase contactUseCase;
    @Inject TransactionUseCase transactionUseCase;
    @Inject SubscriptionUseCase subscriptionUseCase;
    @Inject DebtUseCase debtUseCase;

    @GET
    public Response list() {
        List<TrashItem> all = new ArrayList<>();
        all.addAll(transactionUseCase.listDeleted());
        all.addAll(categoryUseCase.listDeleted());
        all.addAll(contactUseCase.listDeleted());
        all.addAll(subscriptionUseCase.listDeleted());
        all.addAll(debtUseCase.listDeleted());
        all.sort((a, b) -> b.deletedAt().compareTo(a.deletedAt()));

        List<TrashResponse> body = all.stream()
            .map(i -> new TrashResponse(i.id(), i.entityType(), i.label(), i.deletedAt()))
            .collect(Collectors.toList());
        return Response.ok(body).build();
    }

    @POST
    @Path("/{type}/{id}/restore")
    public Response restore(@PathParam("type") String type, @PathParam("id") Long id) {
        if (!VALID_TYPES.contains(type)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"Unknown entity type: " + type + "\"}")
                .build();
        }
        try {
            dispatchRestore(type, id);
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{type}/{id}")
    public Response permanentDelete(@PathParam("type") String type, @PathParam("id") Long id) {
        if (!VALID_TYPES.contains(type)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"Unknown entity type: " + type + "\"}")
                .build();
        }
        try {
            dispatchPermanentDelete(type, id);
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
        return Response.noContent().build();
    }

    private void dispatchRestore(String type, Long id) {
        switch (type) {
            case "transaction" -> transactionUseCase.restore(id);
            case "category" -> categoryUseCase.restore(id);
            case "contact" -> contactUseCase.restore(id);
            case "subscription" -> subscriptionUseCase.restore(id);
            case "debt" -> debtUseCase.restore(id);
        }
    }

    private void dispatchPermanentDelete(String type, Long id) {
        switch (type) {
            case "transaction" -> transactionUseCase.permanentDelete(id);
            case "category" -> categoryUseCase.permanentDelete(id);
            case "contact" -> contactUseCase.permanentDelete(id);
            case "subscription" -> subscriptionUseCase.permanentDelete(id);
            case "debt" -> debtUseCase.permanentDelete(id);
        }
    }
}
