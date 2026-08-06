package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditStatementEstimateResponse(LocalDate periodEnd, BigDecimal estimatedBalance) {}
