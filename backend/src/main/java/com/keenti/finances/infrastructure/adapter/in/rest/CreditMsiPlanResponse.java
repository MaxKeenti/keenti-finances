package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditMsiPlanResponse(Long id, Long transactionId, BigDecimal purchaseAmount,
    int installmentCount, BigDecimal installmentAmount, LocalDate firstInstallmentDate, boolean active) {}
