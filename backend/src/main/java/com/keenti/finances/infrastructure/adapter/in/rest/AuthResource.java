package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.User;
import com.keenti.finances.domain.port.in.AuthUseCase;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthUseCase authUseCase;

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        Optional<User> user = authUseCase.login(request.username(), request.password());
        if (user.isPresent()) {
            return Response.ok(new LoginResponse(user.get().getUsername())).build();
        }
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\":\"Invalid credentials\"}")
                .build();
    }
}
