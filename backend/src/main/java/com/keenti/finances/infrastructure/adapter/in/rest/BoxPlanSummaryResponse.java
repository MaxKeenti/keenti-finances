package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BoxPlanSummaryResponse(
    Long id,
    Long boxId,
    String type,
    String status,
    LocalDateTime createdAt,
    LocalDateTime closedAt,
    BigDecimal completionAmount
) {}
