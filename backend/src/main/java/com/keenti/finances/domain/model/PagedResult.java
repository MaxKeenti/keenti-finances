package com.keenti.finances.domain.model;

import java.util.List;

public record PagedResult<T>(
    List<T> items,
    int pageIndex,
    int pageSize,
    long totalItems,
    int totalPages
) {}
