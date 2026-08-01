package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxBalanceSummary;
import com.keenti.finances.domain.model.BoxCommandResult;
import com.keenti.finances.domain.model.BoxHistoryEntry;
import com.keenti.finances.domain.model.BoxTransferResult;
import com.keenti.finances.domain.port.in.BoxUseCase;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/boxes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BoxResource {

    @Inject
    BoxUseCase boxUseCase;

    @GET
    public Response list(@QueryParam("archived") @DefaultValue("false") boolean archived) {
        return Response.ok(boxUseCase.list(archived).stream()
            .map(BoxResource::toResponse)
            .toList()).build();
    }

    @GET
    @Path("/summary")
    public Response summary() {
        return Response.ok(toResponse(boxUseCase.summary())).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return boxUseCase.getById(id)
            .map(box -> Response.ok(toResponse(box)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Box not found\"}")
                .build());
    }

    @GET
    @Path("/{id}/history")
    public Response history(@PathParam("id") Long id) {
        return Response.ok(boxUseCase.history(id).stream()
            .map(BoxResource::toResponse)
            .toList()).build();
    }

    @POST
    public Response create(@Valid BoxRequest request) {
        Box created = boxUseCase.create(toDomain(request));
        return Response.status(Response.Status.CREATED)
            .entity(toResponse(created))
            .build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid BoxRequest request) {
        return Response.ok(toResponse(boxUseCase.update(id, toDomain(request)))).build();
    }

    @PUT
    @Path("/reorder")
    public Response reorder(@Valid BoxReorderRequest request) {
        return Response.ok(boxUseCase.reorder(request.boxIds()).stream()
            .map(BoxResource::toResponse)
            .toList()).build();
    }

    @POST
    @Path("/{id}/deposit")
    public Response deposit(@PathParam("id") Long id,
                            @Valid BoxAmountRequest request) {
        return Response.ok(toResponse(boxUseCase.deposit(
            id, request.amount(), request.effectiveDate()))).build();
    }

    @POST
    @Path("/{id}/withdraw")
    public Response withdraw(@PathParam("id") Long id,
                             @Valid BoxAmountRequest request) {
        return Response.ok(toResponse(boxUseCase.withdraw(
            id, request.amount(), request.effectiveDate()))).build();
    }

    @POST
    @Path("/{id}/transfer")
    public Response transfer(@PathParam("id") Long id,
                             @Valid BoxTransferRequest request) {
        return Response.ok(toResponse(boxUseCase.transfer(
            id, request.targetBoxId(), request.amount(), request.effectiveDate()))).build();
    }

    @PUT
    @Path("/{id}/movements/{movementId}")
    public Response correctMovement(@PathParam("id") Long id,
                                    @PathParam("movementId") Long movementId,
                                    @Valid BoxAmountRequest request) {
        return Response.ok(toResponse(boxUseCase.correctMovement(
            id, movementId, request.amount(), request.effectiveDate()))).build();
    }

    @POST
    @Path("/{id}/archive")
    @Consumes(MediaType.WILDCARD)
    public Response archive(@PathParam("id") Long id) {
        return Response.ok(toResponse(boxUseCase.archive(id))).build();
    }

    @POST
    @Path("/{id}/restore")
    @Consumes(MediaType.WILDCARD)
    public Response restore(@PathParam("id") Long id) {
        return Response.ok(toResponse(boxUseCase.restore(id))).build();
    }

    private static Box toDomain(BoxRequest request) {
        return new Box(
            null,
            request.name(),
            request.hue(),
            request.icon(),
            request.description(),
            0,
            null,
            false,
            null,
            null,
            0
        );
    }

    private static BoxResponse toResponse(Box box) {
        return new BoxResponse(
            box.getId(),
            box.getName(),
            box.getHue(),
            box.getIcon(),
            box.getDescription(),
            box.getDisplayOrder(),
            box.getBalance(),
            box.isArchived(),
            box.getCreatedAt(),
            box.getUpdatedAt(),
            box.getVersion()
        );
    }

    private static BoxMovementResponse toResponse(BoxHistoryEntry entry) {
        return new BoxMovementResponse(
            entry.id(),
            entry.type().name(),
            entry.amount(),
            entry.effectiveDate(),
            entry.createdAt(),
            entry.runningBalance(),
            entry.relatedBoxId(),
            entry.relatedBoxName(),
            entry.relatedTransactionId(),
            entry.relatedTransactionDescription(),
            entry.relatedTransactionChanged(),
            entry.relatedTransactionRemoved()
        );
    }

    private static BoxBalanceSummaryResponse toResponse(BoxBalanceSummary summary) {
        return new BoxBalanceSummaryResponse(
            summary.netBalance(),
            summary.inBoxes(),
            summary.availableToSpend()
        );
    }

    private static BoxCommandResponse toResponse(BoxCommandResult result) {
        return new BoxCommandResponse(
            toResponse(result.box()),
            toResponse(result.summary())
        );
    }

    private static BoxTransferResponse toResponse(BoxTransferResult result) {
        return new BoxTransferResponse(
            toResponse(result.sourceBox()),
            toResponse(result.targetBox()),
            toResponse(result.summary())
        );
    }
}
