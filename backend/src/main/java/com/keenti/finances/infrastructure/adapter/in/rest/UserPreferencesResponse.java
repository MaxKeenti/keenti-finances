package com.keenti.finances.infrastructure.adapter.in.rest;

public record UserPreferencesResponse(
    int primaryHue,
    String headingFont,
    String bodyFont,
    String locale,
    int transactionPageSize,
    String transactionSortBy,
    String transactionSortDirection,
    String mobilePinnedNavItems,
    boolean dockMagnification
) {}
