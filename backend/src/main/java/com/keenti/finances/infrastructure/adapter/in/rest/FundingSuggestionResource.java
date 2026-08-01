package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.FundingSuggestionSet;
import com.keenti.finances.domain.port.in.FundingTriggerUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;

@Path("/api/funding-triggers/suggestions")
@Produces(MediaType.APPLICATION_JSON)
public class FundingSuggestionResource {

    @Inject
    FundingTriggerUseCase fundingTriggerUseCase;

    @GET
    public Response suggestions(@QueryParam("categoryId") Long categoryId,
                                @QueryParam("ingressAmount") BigDecimal ingressAmount) {
        return Response.ok(toResponse(
            fundingTriggerUseCase.suggestions(categoryId, ingressAmount))).build();
    }

    static FundingSuggestionSetResponse toResponse(FundingSuggestionSet result) {
        return new FundingSuggestionSetResponse(
            result.categoryId(),
            result.ingressAmount(),
            result.suggestions().stream()
                .map(suggestion -> new FundingSuggestionResponse(
                    suggestion.triggerId(), suggestion.boxId(), suggestion.boxName(),
                    suggestion.strategy().name(), suggestion.suggestedAmount()))
                .toList(),
            result.combinedTotal());
    }
}
