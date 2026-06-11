package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.port.in.ContactUseCase;
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

@Path("/api/contacts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContactResource {

    @Inject
    ContactUseCase contactUseCase;

    @GET
    public Response list() {
        var body = contactUseCase.list().stream()
                .map(c -> new ContactResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail()))
                .toList();
        return Response.ok(body).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return contactUseCase.getById(id)
                .map(c -> Response.ok(new ContactResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail())).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Contact not found\"}")
                        .build());
    }

    @POST
    public Response create(@Valid ContactRequest request) {
        Contact created = contactUseCase.create(new Contact(null, request.name(), request.phone(), request.email()));
        return Response.status(Response.Status.CREATED)
                .entity(new ContactResponse(created.getId(), created.getName(), created.getPhone(), created.getEmail()))
                .build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid ContactRequest request) {
        Contact updated = contactUseCase.update(id, new Contact(null, request.name(), request.phone(), request.email()));
        return Response.ok(new ContactResponse(updated.getId(), updated.getName(), updated.getPhone(), updated.getEmail())).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        contactUseCase.delete(id);
        return Response.noContent().build();
    }
}
