package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;

public record BulkPaymentItemResponse(
    Long debtId,
    String description,
    BigDecimal applied,
    BigDecimal remaining,
    String debtStatus
) {}
