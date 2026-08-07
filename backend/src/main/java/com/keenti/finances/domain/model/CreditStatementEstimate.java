package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Continuously calculated statement figures for the Account's configured billing cycle. */
public record CreditStatementEstimate(LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
                                      BigDecimal estimatedBalance) {}
