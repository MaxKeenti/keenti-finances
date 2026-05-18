package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.application.service.BillingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/subscriptions/generate-billing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BillingResource {

    @Inject
    BillingService billingService;

    @POST
    public Response generateBilling() {
        int generated = billingService.generateBilling();
        return Response.ok("{\"generated\":" + generated + "}").build();
    }
}
