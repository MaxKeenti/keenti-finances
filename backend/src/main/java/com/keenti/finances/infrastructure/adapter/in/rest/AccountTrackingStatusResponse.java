package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountTrackingStatusResponse(
    boolean active,
    boolean setupRequired,
    LocalDate activatedAt,
    BigDecimal transactionNetBalance,
    BigDecimal accountNetBalance
) {}
