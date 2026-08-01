package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.SpendingBudgetPeriod;
import com.keenti.finances.domain.model.SpendingBudgetRevision;
import com.keenti.finances.domain.model.SpendingBudgetRevisionPreview;
import com.keenti.finances.domain.model.SpendingBudgetSnapshot;
import com.keenti.finances.domain.model.SpendingBudgetTerms;
import com.keenti.finances.domain.port.in.SpendingBudgetUseCase;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/boxes/{boxId}/plans/spending-budget")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SpendingBudgetResource {

    @Inject
    SpendingBudgetUseCase spendingBudgetUseCase;

    @Inject
    UserTimeZoneProvider userTimeZoneProvider;

    @POST
    public Response create(@PathParam("boxId") Long boxId,
                           @Valid SpendingBudgetTermsRequest request) {
        SpendingBudgetSnapshot created = spendingBudgetUseCase.create(
            boxId, toTerms(request));
        return Response.status(Response.Status.CREATED)
            .entity(toResponse(created))
            .build();
    }

    @GET
    public Response getActive(@PathParam("boxId") Long boxId) {
        return Response.ok(toResponse(
            spendingBudgetUseCase.getActive(boxId))).build();
    }

    @GET
    @Path("/{planId}")
    public Response get(@PathParam("boxId") Long boxId,
                        @PathParam("planId") Long planId) {
        return Response.ok(toResponse(
            spendingBudgetUseCase.get(boxId, planId))).build();
    }

    @POST
    @Path("/{planId}/revision-preview")
    public Response previewRevision(@PathParam("boxId") Long boxId,
                                    @PathParam("planId") Long planId,
                                    @Valid SpendingBudgetTermsRequest request) {
        return Response.ok(toResponse(spendingBudgetUseCase.previewRevision(
            boxId, planId, toTerms(request)))).build();
    }

    @POST
    @Path("/{planId}/revisions")
    public Response applyRevision(@PathParam("boxId") Long boxId,
                                  @PathParam("planId") Long planId,
                                  @Valid SpendingBudgetTermsRequest request) {
        return Response.ok(toResponse(spendingBudgetUseCase.applyRevision(
            boxId, planId, toTerms(request)))).build();
    }

    @POST
    @Path("/{planId}/end")
    @Consumes(MediaType.WILDCARD)
    public Response end(@PathParam("boxId") Long boxId,
                        @PathParam("planId") Long planId) {
        return Response.ok(toResponse(
            spendingBudgetUseCase.end(boxId, planId))).build();
    }

    private static SpendingBudgetTerms toTerms(SpendingBudgetTermsRequest request) {
        return new SpendingBudgetTerms(
            request.desiredBalance(), request.cadence(),
            request.anchorWeekday(), request.anchorDayOfMonth());
    }

    private SpendingBudgetResponse toResponse(SpendingBudgetSnapshot snapshot) {
        var plan = snapshot.plan();
        var terms = snapshot.revision();
        var today = userTimeZoneProvider.today();
        return new SpendingBudgetResponse(
            plan.id(), plan.boxId(), plan.type().name(), plan.status().name(),
            terms.desiredBalance(), terms.planRevision().cadence().name(),
            terms.planRevision().anchorWeekday(), terms.planRevision().anchorDayOfMonth(),
            snapshot.currentBalance(), snapshot.suggestedTopUp(),
            toResponse(snapshot.currentPeriod()),
            snapshot.periods().stream().map(SpendingBudgetResource::toResponse).toList(),
            snapshot.revisions().stream()
                .map(revision -> toResponse(revision, today)).toList(),
            plan.createdAt(), plan.updatedAt(), plan.closedAt(), plan.completionAmount());
    }

    private static SpendingBudgetRevisionResponse toResponse(
            SpendingBudgetRevision revision, java.time.LocalDate today) {
        var planRevision = revision.planRevision();
        return new SpendingBudgetRevisionResponse(
            planRevision.id(), planRevision.effectiveFrom(),
            planRevision.cadence().name(), planRevision.anchorWeekday(),
            planRevision.anchorDayOfMonth(), revision.desiredBalance(),
            planRevision.createdAt(), planRevision.supersededAt(),
            planRevision.supersededAt() == null
                && planRevision.effectiveFrom().isAfter(today));
    }

    private static SpendingBudgetPeriodResponse toResponse(
            SpendingBudgetPeriod period) {
        var planPeriod = period.planPeriod();
        return new SpendingBudgetPeriodResponse(
            planPeriod.id(), planPeriod.revisionId(), planPeriod.periodStart(),
            planPeriod.periodEndExclusive().minusDays(1), planPeriod.openingBalance(),
            planPeriod.closingBalance(), planPeriod.netProgress(), period.deposits(),
            period.withdrawals(), period.transfersIn(), period.transfersOut(),
            period.fundedSpending(), period.suggestedTopUp(), planPeriod.evaluatedAt());
    }

    private static SpendingBudgetRevisionPreviewResponse toResponse(
            SpendingBudgetRevisionPreview preview) {
        return new SpendingBudgetRevisionPreviewResponse(
            preview.planId(), preview.effectiveFrom(), preview.cadence().name(),
            preview.anchorWeekday(), preview.anchorDayOfMonth(),
            preview.desiredBalance(), preview.currentBalance(), preview.suggestedTopUp());
    }
}
