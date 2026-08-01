package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.SavingGoalDetails;
import com.keenti.finances.domain.model.SavingGoalPeriod;
import com.keenti.finances.domain.model.SavingGoalRevision;
import com.keenti.finances.domain.model.SavingGoalRevisionPreview;
import com.keenti.finances.domain.model.SavingGoalTermsChange;
import com.keenti.finances.domain.port.in.SavingGoalUseCase;
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
import java.time.LocalDate;

@Path("/api/boxes/{boxId}/plans")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SavingGoalResource {

    @Inject
    SavingGoalUseCase savingGoalUseCase;

    @Inject
    UserTimeZoneProvider userTimeZoneProvider;

    @GET
    public Response list(@PathParam("boxId") Long boxId) {
        return Response.ok(savingGoalUseCase.listPlans(boxId).stream()
            .map(SavingGoalResource::toSummary)
            .toList()).build();
    }

    @POST
    @Path("/saving-goal")
    public Response create(@PathParam("boxId") Long boxId,
                           @Valid SavingGoalRequest request) {
        return Response.status(Response.Status.CREATED)
            .entity(toResponse(savingGoalUseCase.create(boxId, toDomain(request))))
            .build();
    }

    @GET
    @Path("/saving-goal")
    public Response getActive(@PathParam("boxId") Long boxId) {
        return savingGoalUseCase.getActive(boxId)
            .map(goal -> Response.ok(toResponse(goal)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Active Saving Goal not found\"}")
                .build());
    }

    @GET
    @Path("/saving-goal/{planId}")
    public Response get(@PathParam("boxId") Long boxId,
                        @PathParam("planId") Long planId) {
        return savingGoalUseCase.get(boxId, planId)
            .map(goal -> Response.ok(toResponse(goal)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Saving Goal not found\"}")
                .build());
    }

    @POST
    @Path("/saving-goal/{planId}/revision-preview")
    public Response previewRevision(@PathParam("boxId") Long boxId,
                                    @PathParam("planId") Long planId,
                                    @Valid SavingGoalRequest request) {
        return Response.ok(toResponse(savingGoalUseCase.previewRevision(
            boxId, planId, toDomain(request)))).build();
    }

    @POST
    @Path("/saving-goal/{planId}/revisions")
    public Response applyRevision(@PathParam("boxId") Long boxId,
                                  @PathParam("planId") Long planId,
                                  @Valid SavingGoalRequest request) {
        return Response.ok(toResponse(savingGoalUseCase.applyRevision(
            boxId, planId, toDomain(request)))).build();
    }

    @POST
    @Path("/saving-goal/{planId}/complete")
    @Consumes(MediaType.WILDCARD)
    public Response complete(@PathParam("boxId") Long boxId,
                             @PathParam("planId") Long planId) {
        return Response.ok(toResponse(
            savingGoalUseCase.confirmCompletion(boxId, planId))).build();
    }

    @POST
    @Path("/saving-goal/{planId}/abandon")
    @Consumes(MediaType.WILDCARD)
    public Response abandon(@PathParam("boxId") Long boxId,
                            @PathParam("planId") Long planId) {
        return Response.ok(toResponse(
            savingGoalUseCase.abandon(boxId, planId))).build();
    }

    private SavingGoalResponse toResponse(SavingGoalDetails details) {
        SavingGoalRevision terms = details.currentRevision();
        LocalDate today = userTimeZoneProvider.today();
        return new SavingGoalResponse(
            details.plan().id(),
            details.plan().boxId(),
            details.plan().type().name(),
            details.plan().status().name(),
            terms.targetAmount(),
            terms.targetDate(),
            terms.cadence().name(),
            terms.anchorWeekday(),
            terms.anchorDayOfMonth(),
            terms.regularCommitment(),
            details.boxBalance(),
            details.remainingAmount(),
            details.progressPercent(),
            details.arrears(),
            details.currentCommitment(),
            details.projectedCompletionDate(),
            details.suggestedExtensionDate(),
            toResponse(details.currentPeriod()),
            details.periods().stream().map(SavingGoalResource::toResponse).toList(),
            details.revisions().stream()
                .map(revision -> toResponse(revision, today))
                .toList(),
            details.plan().createdAt(),
            details.plan().updatedAt(),
            details.plan().closedAt(),
            details.plan().completionAmount()
        );
    }

    private static BoxPlanSummaryResponse toSummary(BoxPlan plan) {
        return new BoxPlanSummaryResponse(
            plan.id(), plan.boxId(), plan.type().name(), plan.status().name(),
            plan.createdAt(), plan.closedAt(), plan.completionAmount());
    }

    private static SavingGoalPeriodResponse toResponse(SavingGoalPeriod period) {
        if (period == null) {
            return null;
        }
        return new SavingGoalPeriodResponse(
            period.id(),
            period.revisionId(),
            period.periodStart(),
            period.periodEndExclusive().minusDays(1),
            period.openingBalance(),
            period.closingBalance(),
            period.netProgress(),
            period.regularCommitment(),
            period.openingArrears(),
            period.requiredAmount(),
            period.arrearsCovered(),
            period.regularProgress(),
            period.extraProgress(),
            period.shortfall(),
            period.status().name(),
            period.evaluatedAt()
        );
    }

    private static SavingGoalRevisionResponse toResponse(
            SavingGoalRevision revision, LocalDate today) {
        return new SavingGoalRevisionResponse(
            revision.id(),
            revision.effectiveFrom(),
            revision.cadence().name(),
            revision.anchorWeekday(),
            revision.anchorDayOfMonth(),
            revision.targetAmount(),
            revision.targetDate(),
            revision.regularCommitment(),
            revision.createdAt(),
            revision.supersededAt(),
            revision.supersededAt() == null && revision.effectiveFrom().isAfter(today)
        );
    }

    private static SavingGoalRevisionPreviewResponse toResponse(
            SavingGoalRevisionPreview preview) {
        return new SavingGoalRevisionPreviewResponse(
            preview.effectiveFrom(), preview.targetAmount(), preview.targetDate(),
            preview.cadence().name(), preview.anchorWeekday(),
            preview.anchorDayOfMonth(), preview.regularCommitment(),
            preview.remainingPeriods(), preview.boxBalance(),
            preview.remainingAmount(), preview.currentArrears(),
            preview.projectedCompletionDate(), preview.suggestedExtensionDate());
    }

    private static SavingGoalTermsChange toDomain(SavingGoalRequest request) {
        if (request == null) {
            return null;
        }
        return new SavingGoalTermsChange(
            request.targetAmount(), request.targetDate(), request.cadence(),
            request.anchorWeekday(), request.anchorDayOfMonth(),
            request.regularCommitment());
    }
}
