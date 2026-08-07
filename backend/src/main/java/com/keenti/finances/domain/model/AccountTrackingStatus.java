package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountTrackingStatus(
    boolean active,
    boolean setupRequired,
    LocalDate activatedAt,
    BigDecimal transactionNetBalance,
    BigDecimal accountNetBalance
) {}
