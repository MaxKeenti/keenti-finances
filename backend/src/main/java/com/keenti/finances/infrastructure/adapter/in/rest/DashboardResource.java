package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.DashboardOverview;
import com.keenti.finances.domain.model.DashboardSummary;
import com.keenti.finances.domain.port.in.DashboardOverviewUseCase;
import com.keenti.finances.domain.port.in.DashboardUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Year;

@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    DashboardUseCase dashboardUseCase;

    @Inject
    DashboardOverviewUseCase dashboardOverviewUseCase;

    @GET
    @Path("/summary")
    public Response getSummary(@QueryParam("year") @DefaultValue("current") String yearParam) {
        int year;
        if ("current".equalsIgnoreCase(yearParam)) {
            year = Year.now().getValue();
        } else {
            try {
                year = Integer.parseInt(yearParam);
                if (year < 1900 || year > 9999) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("{\"error\":\"Invalid year parameter: must be between 1900 and 9999\"}")
                            .build();
                }
            } catch (NumberFormatException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Invalid year parameter: '" + yearParam + "' is not a valid year\"}")
                        .build();
            }
        }

        DashboardSummary summary = dashboardUseCase.getSummary(year);
        return Response.ok(summary).build();
    }

    /**
     * The composed dashboard read model (Phase 4).
     *
     * <p>Additive and read-only. It generates no billing, advances no generation
     * cursor and moves no money; the only write any part of it can cause is the
     * idempotent lazy Box Plan period evaluation ADR-0021 already performs on
     * every plan read.
     *
     * <p>Sections are independently available: a failure inside one arrives as
     * {@code {"status":"unavailable"}} with no data, and the rest of the
     * response still carries its figures. The year parameter scopes the history
     * section only — the current-position totals are all-time.
     */
    @GET
    @Path("/overview")
    public Response getOverview(@QueryParam("year") @DefaultValue("current") String yearParam) {
        Integer year = null;
        // Resolve "current" inside the overview's guarded calendar read so a
        // missing zone cannot hide unrelated balances and expected receipts.
        if (!"current".equalsIgnoreCase(yearParam)) {
            try {
                year = Integer.parseInt(yearParam);
            } catch (NumberFormatException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Invalid year parameter: '" + yearParam + "' is not a valid year\"}")
                        .build();
            }
            if (year < 1900 || year > 9999) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Invalid year parameter: must be between 1900 and 9999\"}")
                        .build();
            }
        }

        DashboardOverview overview = dashboardOverviewUseCase.getOverview(year);
        return Response.ok(overview).build();
    }
}
