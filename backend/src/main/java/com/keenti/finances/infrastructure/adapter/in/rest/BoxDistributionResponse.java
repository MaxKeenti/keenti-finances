package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BoxDistributionResponse(
    Long boxId,
    String boxName,
    BigDecimal amount,
    int lineOrder,
    LocalDate effectiveDate
) {}
