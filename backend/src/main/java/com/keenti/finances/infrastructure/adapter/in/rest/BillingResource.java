package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.application.service.BillingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.OptionalInt;

@Path("/api/subscriptions/{id}/generate-billing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BillingResource {

    @Inject
    BillingService billingService;

    @POST
    public Response generateBilling(@PathParam("id") Long id) {
        OptionalInt generated = billingService.generateForSubscription(id);
        if (generated.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Subscription not found\"}").build();
        }
        return Response.ok("{\"generated\":" + generated.getAsInt() + "}").build();
    }
}
