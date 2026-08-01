package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.FundingTrigger;
import com.keenti.finances.domain.port.in.FundingTriggerUseCase;
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
import java.util.Locale;

@Path("/api/boxes/{boxId}/funding-triggers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FundingTriggerResource {

    @Inject
    FundingTriggerUseCase fundingTriggerUseCase;

    @GET
    public Response list(@PathParam("boxId") Long boxId) {
        return Response.ok(fundingTriggerUseCase.list(boxId).stream()
            .map(FundingTriggerResource::toResponse)
            .toList()).build();
    }

    @GET
    @Path("/{triggerId}")
    public Response getById(@PathParam("boxId") Long boxId,
                            @PathParam("triggerId") Long triggerId) {
        return fundingTriggerUseCase.getById(boxId, triggerId)
            .map(trigger -> Response.ok(toResponse(trigger)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Funding Trigger not found\"}")
                .build());
    }

    @POST
    public Response create(@PathParam("boxId") Long boxId,
                           @Valid FundingTriggerRequest request) {
        FundingTrigger created = fundingTriggerUseCase.create(
            boxId, toDomain(boxId, request, request.enabled() == null || request.enabled()));
        return Response.status(Response.Status.CREATED)
            .entity(toResponse(created))
            .build();
    }

    @PUT
    @Path("/{triggerId}")
    public Response update(@PathParam("boxId") Long boxId,
                           @PathParam("triggerId") Long triggerId,
                           @Valid FundingTriggerRequest request) {
        FundingTrigger existing = fundingTriggerUseCase.getById(boxId, triggerId)
            .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                "Funding Trigger not found: " + triggerId));
        boolean enabled = request.enabled() != null ? request.enabled() : existing.enabled();
        FundingTrigger updated = fundingTriggerUseCase.update(
            boxId, triggerId, toDomain(boxId, request, enabled));
        return Response.ok(toResponse(updated)).build();
    }

    @PUT
    @Path("/{triggerId}/enabled")
    public Response setEnabled(@PathParam("boxId") Long boxId,
                               @PathParam("triggerId") Long triggerId,
                               @Valid FundingTriggerEnabledRequest request) {
        return Response.ok(toResponse(fundingTriggerUseCase.setEnabled(
            boxId, triggerId, request.enabled()))).build();
    }

    @DELETE
    @Path("/{triggerId}")
    public Response delete(@PathParam("boxId") Long boxId,
                           @PathParam("triggerId") Long triggerId) {
        fundingTriggerUseCase.delete(boxId, triggerId);
        return Response.noContent().build();
    }

    private static FundingTrigger toDomain(
            Long boxId, FundingTriggerRequest request, boolean enabled) {
        return new FundingTrigger(
            null, boxId, null, request.categoryId(), null,
            parseStrategy(request.strategy()), request.fixedAmount(), request.percentage(),
            enabled, null, null);
    }

    private static FundingTrigger.Strategy parseStrategy(String strategy) {
        try {
            return FundingTrigger.Strategy.valueOf(strategy.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new jakarta.ws.rs.BadRequestException(
                "Unknown Funding Trigger strategy: " + strategy);
        }
    }

    static FundingTriggerResponse toResponse(FundingTrigger trigger) {
        return new FundingTriggerResponse(
            trigger.id(), trigger.boxId(), trigger.boxName(),
            trigger.categoryId(), trigger.categoryName(), trigger.strategy().name(),
            trigger.fixedAmount(), trigger.percentage(), trigger.enabled(),
            trigger.createdAt(), trigger.updatedAt());
    }
}
