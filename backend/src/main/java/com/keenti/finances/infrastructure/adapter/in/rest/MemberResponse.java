package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MemberResponse(
    Long id,
    Long subscriptionId,
    Long contactId,
    String contactName,
    BigDecimal shareAmount,
    LocalDateTime createdAt
) {}
