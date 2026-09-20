package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;

public record BulkPaymentResponse(
    Long contactId,
    String contactName,
    String direction,
    BigDecimal totalAmount,
    BigDecimal totalApplied,
    BigDecimal totalUnused,
    List<BulkPaymentItemResponse> payments
) {}
