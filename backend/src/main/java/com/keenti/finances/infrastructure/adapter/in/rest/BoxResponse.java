package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BoxResponse(
    Long id,
    String name,
    int hue,
    String icon,
    String description,
    int displayOrder,
    BigDecimal balance,
    boolean archived,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long version
) {}
