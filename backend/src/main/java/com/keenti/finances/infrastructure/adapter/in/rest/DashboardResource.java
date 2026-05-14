package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.DashboardSummary;
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
}
