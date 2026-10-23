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
    boolean dockMagnification,
    String timeZone,
    String themeMode,
    String primaryCurrency,
    /** The currencies a User may choose from (ADR-0024). */
    java.util.List<String> supportedCurrencies
) {}
