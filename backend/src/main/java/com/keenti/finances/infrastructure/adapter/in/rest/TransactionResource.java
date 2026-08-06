package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.model.BoxDistribution;
import com.keenti.finances.domain.model.BoxFunding;
import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.in.CategoryUseCase;
import com.keenti.finances.domain.port.in.ContactUseCase;
import com.keenti.finances.domain.port.in.FundingTriggerUseCase;
import com.keenti.finances.domain.port.in.FinancialAccountUseCase;
import com.keenti.finances.domain.port.in.TransactionUseCase;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

@Path("/api/transactions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TransactionResource {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of(
        "transactionDate",
        "amount",
        "direction",
        "description",
        "categoryName",
        "contactName"
    );

    @Inject
    TransactionUseCase transactionUseCase;

    @Inject
    CategoryUseCase categoryUseCase;

    @Inject
    ContactUseCase contactUseCase;

    @Inject
    FundingTriggerUseCase fundingTriggerUseCase;

    @Inject
    FinancialAccountUseCase financialAccountUseCase;

    @GET
    public Response list(
        @QueryParam("page") Integer page,
        @QueryParam("pageSize") Integer pageSize,
        @QueryParam("sortBy") String sortBy,
        @QueryParam("sortDirection") String sortDirection
    ) {
        if (page != null || pageSize != null || sortBy != null || sortDirection != null) {
            int requestedPage = page != null ? page : 0;
            int requestedPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
            String requestedSortBy = sortBy != null && !sortBy.isBlank() ? sortBy : "transactionDate";
            String requestedSortDirection = sortDirection != null && !sortDirection.isBlank()
                    ? sortDirection.toLowerCase(Locale.ROOT)
                    : "desc";

            if (requestedPage < 0 || requestedPageSize < 1 || requestedPageSize > MAX_PAGE_SIZE) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Invalid pagination parameters\"}")
                        .build();
            }
            if (!SORT_FIELDS.contains(requestedSortBy)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Invalid sortBy\"}")
                        .build();
            }
            if (!Set.of("asc", "desc").contains(requestedSortDirection)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Invalid sortDirection\"}")
                        .build();
            }

            var pageResult = transactionUseCase.listPage(
                requestedPage,
                requestedPageSize,
                requestedSortBy,
                "desc".equals(requestedSortDirection)
            );
            var body = new TransactionPageResponse(
                pageResult.items().stream().map(this::toResponse).toList(),
                pageResult.pageIndex(),
                pageResult.pageSize(),
                pageResult.totalItems(),
                pageResult.totalPages(),
                requestedSortBy,
                requestedSortDirection
            );
            return Response.ok(body).build();
        }

        var body = transactionUseCase.list().stream()
                .map(this::toResponse)
                .toList();
        return Response.ok(body).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return transactionUseCase.getById(id)
                .map(t -> Response.ok(toResponse(t)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Transaction not found\"}")
                        .build());
    }

    @GET
    @Path("/funding-suggestions")
    public Response fundingSuggestions(@QueryParam("categoryId") Long categoryId,
                                       @QueryParam("ingressAmount") java.math.BigDecimal ingressAmount) {
        return Response.ok(FundingSuggestionResource.toResponse(
            fundingTriggerUseCase.suggestions(categoryId, ingressAmount))).build();
    }

    @POST
    public Response create(@Valid TransactionRequest request) {
        categoryUseCase.getById(request.categoryId())
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Category not found: " + request.categoryId() + "\"}")
                        .build()));
        if (request.contactId() != null) {
            contactUseCase.getById(request.contactId())
                    .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                            .build()));
        }
        Transaction created = transactionUseCase.create(toTransaction(null, request));
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(created))
                .build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid TransactionRequest request) {
        categoryUseCase.getById(request.categoryId())
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Category not found: " + request.categoryId() + "\"}")
                        .build()));
        if (request.contactId() != null) {
            contactUseCase.getById(request.contactId())
                    .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\":\"Contact not found: " + request.contactId() + "\"}")
                            .build()));
        }
        Transaction requested = toTransaction(id, request);
        if (request.boxFunding() == null) {
            var existing = transactionUseCase.getById(id).orElseThrow(() ->
                new jakarta.ws.rs.NotFoundException("Transaction not found: " + id));
            requested = new Transaction(
                requested.getId(), requested.getAmount(), requested.getDirection(),
                requested.getDescription(), requested.getTransactionDate(),
                requested.getCategoryId(), requested.getContactId(),
                existing.getSubscriptionId(), requested.getAccountId(), existing.getBoxFunding(),
                requested.getBoxDistributions());
        }
        Transaction updated = transactionUseCase.update(id, requested);
        return Response.ok(toResponse(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        transactionUseCase.delete(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/link-subscription")
    public Response linkSubscription(@PathParam("id") Long id, LinkSubscriptionRequest request) {
        Transaction updated = transactionUseCase.linkSubscription(id, request != null ? request.subscriptionId() : null);
        return Response.ok(toResponse(updated)).build();
    }

    private Transaction toTransaction(Long id, TransactionRequest r) {
        return new Transaction(id, r.amount(), r.direction(), r.description(),
                r.transactionDate(), r.categoryId(), r.contactId(), null,
                r.accountId(), toBoxFunding(r.boxFunding()), toBoxDistributions(r.boxDistributions()));
    }

    private java.util.List<BoxDistribution> toBoxDistributions(
            java.util.List<BoxDistributionRequest> distributions) {
        if (distributions == null || distributions.isEmpty()) {
            return java.util.List.of();
        }
        return IntStream.range(0, distributions.size())
            .mapToObj(index -> new BoxDistribution(
                distributions.get(index).boxId(), distributions.get(index).amount(), index))
            .toList();
    }

    private java.util.List<BoxFunding> toBoxFunding(java.util.List<BoxFundingRequest> funding) {
        if (funding == null || funding.isEmpty()) {
            return java.util.List.of();
        }
        return IntStream.range(0, funding.size())
            .mapToObj(index -> new BoxFunding(
                funding.get(index).boxId(), funding.get(index).amount(), index))
            .toList();
    }

    private TransactionResponse toResponse(Transaction t) {
        Optional<Category> category = t.getCategoryId() != null
                ? categoryUseCase.getById(t.getCategoryId())
                : Optional.empty();
        String categoryName = category.map(Category::getName).orElse(null);
        Integer categoryHue = category.map(Category::getHue).orElse(null);
        String contactName = t.getContactId() != null
                ? contactUseCase.getById(t.getContactId()).map(Contact::getName).orElse(null)
                : null;
        Optional<FinancialAccount> account = t.getAccountId() != null
                ? financialAccountUseCase.getById(t.getAccountId())
                : Optional.empty();
        return new TransactionResponse(
            t.getId(), t.getAmount(), t.getDirection(), t.getDescription(),
            t.getTransactionDate(), t.getCategoryId(), categoryName, categoryHue,
            t.getContactId(), contactName, t.getSubscriptionId(),
            t.getAccountId(), account.map(FinancialAccount::getName).orElse(null),
            account.map(FinancialAccount::getKind).orElse(null),
            t.getBoxFunding().stream()
                .map(f -> new BoxFundingResponse(
                    f.boxId(), f.boxName(), f.amount(), f.lineOrder()))
                .toList(),
            t.getBoxDistributions().stream()
                .map(d -> new BoxDistributionResponse(
                    d.boxId(), d.boxName(), d.amount(), d.lineOrder(), d.effectiveDate()))
                .toList(),
            t.getAvailableToSpendAmount()
        );
    }
}
