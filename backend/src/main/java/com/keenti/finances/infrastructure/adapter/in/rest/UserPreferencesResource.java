package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/user/preferences")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserPreferencesResource {

    @Inject
    UserContext userContext;

    @GET
    public Response get() {
        UserEntity user = UserEntity.findById(userContext.getUserId());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new UserPreferencesResponse(user.primaryHue, user.headingFont, user.bodyFont)).build();
    }

    @PUT
    @Transactional
    public Response update(@Valid UserPreferencesRequest request) {
        UserEntity user = UserEntity.findById(userContext.getUserId());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        user.primaryHue = request.primaryHue();
        user.headingFont = request.headingFont();
        user.bodyFont = request.bodyFont();
        return Response.ok(new UserPreferencesResponse(user.primaryHue, user.headingFont, user.bodyFont)).build();
    }
}
