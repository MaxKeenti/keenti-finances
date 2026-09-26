package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.port.in.PlanningPreviewUseCase;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * D5 planning preview (slice 5B). POST because the scenario is a body, not
 * because anything is created: the preview performs no financial write. The
 * only side effect is ADR-0021's lazy Box Plan evaluation. Not linked from the
 * interface until slice 5C.
 *
 * <p>Well-formed requests always get 200, including business-invalid rows
 * (status "unavailable", projected null). Malformed or oversized requests are
 * rejected with 400 before any read.
 */
@Path("/api/planning/preview")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlanningPreviewResource {

    @Inject
    PlanningPreviewUseCase planningPreviewUseCase;

    @POST
    public PlanningPreviewResponse preview(@NotNull @Valid PlanningPreviewRequest request) {
        return PlanningPreviewResponse.from(planningPreviewUseCase.preview(request.toDomain()));
    }
}
