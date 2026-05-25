package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.port.in.CategoryUseCase;
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
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/categories")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CategoryResource {

    @Inject
    CategoryUseCase categoryUseCase;

    @GET
    public Response list() {
        List<CategoryResponse> body = categoryUseCase.list().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getHue()))
                .collect(Collectors.toList());
        return Response.ok(body).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return categoryUseCase.getById(id)
                .map(c -> Response.ok(new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getHue())).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Category not found\"}")
                        .build());
    }

    @POST
    public Response create(@Valid CategoryRequest request) {
        Category created = categoryUseCase.create(new Category(null, request.name(), request.type(), request.hue()));
        return Response.status(Response.Status.CREATED)
                .entity(new CategoryResponse(created.getId(), created.getName(), created.getType(), created.getColor()))
                .build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid CategoryRequest request) {
        Category updated = categoryUseCase.update(id, new Category(null, request.name(), request.type(), request.hue()));
        return Response.ok(new CategoryResponse(updated.getId(), updated.getName(), updated.getType(), updated.getColor())).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        categoryUseCase.delete(id);
        return Response.noContent().build();
    }
}
