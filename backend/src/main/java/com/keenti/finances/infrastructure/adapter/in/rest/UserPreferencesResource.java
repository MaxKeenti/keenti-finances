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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.ZoneId;

@Path("/api/user/preferences")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserPreferencesResource {

    private static final Set<Integer> ALLOWED_TRANSACTION_PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final Set<String> ALLOWED_MOBILE_PINNED_NAV_ITEMS = Set.of(
        "/",
        "/transactions",
        "/boxes",
        "/subscriptions",
        "/debts",
        "/settings"
    );

    @Inject
    UserContext userContext;

    @GET
    public Response get() {
        UserEntity user = UserEntity.findById(userContext.getUserId());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(toResponse(user)).build();
    }

    @PUT
    @Transactional
    public Response update(@Valid UserPreferencesRequest request) {
        if (!ALLOWED_TRANSACTION_PAGE_SIZES.contains(request.transactionPageSize())
            || !isValidPinnedNavItems(request.mobilePinnedNavItems())
            || !isValidTimeZone(request.timeZone())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        UserEntity user = UserEntity.findById(userContext.getUserId());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        user.primaryHue = request.primaryHue();
        user.headingFont = request.headingFont();
        user.bodyFont = request.bodyFont();
        user.locale = request.locale();
        user.transactionPageSize = request.transactionPageSize();
        user.transactionSortBy = request.transactionSortBy();
        user.transactionSortDirection = request.transactionSortDirection();
        user.mobilePinnedNavItems = request.mobilePinnedNavItems();
        user.dockMagnification = request.dockMagnification();
        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            user.timeZone = request.timeZone();
        }
        user.themeMode = request.themeMode();
        return Response.ok(toResponse(user)).build();
    }

    private static UserPreferencesResponse toResponse(UserEntity user) {
        return new UserPreferencesResponse(
            user.primaryHue,
            user.headingFont,
            user.bodyFont,
            user.locale,
            user.transactionPageSize,
            user.transactionSortBy,
            user.transactionSortDirection,
            user.mobilePinnedNavItems,
            user.dockMagnification,
            user.timeZone,
            user.themeMode
        );
    }

    private static boolean isValidPinnedNavItems(String csv) {
        List<String> items = List.of(csv.split(",", -1));
        if (items.size() != 3) {
            return false;
        }

        Set<String> unique = new HashSet<>(items);
        return unique.size() == items.size() && ALLOWED_MOBILE_PINNED_NAV_ITEMS.containsAll(items);
    }

    private static boolean isValidTimeZone(String timeZone) {
        return timeZone == null
            || timeZone.isBlank()
            || ZoneId.getAvailableZoneIds().contains(timeZone);
    }
}
