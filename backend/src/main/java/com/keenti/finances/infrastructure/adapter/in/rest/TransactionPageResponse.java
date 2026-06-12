package com.keenti.finances.infrastructure.adapter.in.rest;

import java.util.List;

public record TransactionPageResponse(
    List<TransactionResponse> items,
    int pageIndex,
    int pageSize,
    long totalItems,
    int totalPages,
    String sortBy,
    String sortDirection
) {}
