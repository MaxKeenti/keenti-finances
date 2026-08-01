package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;

public record BoxFundingResponse(
    Long boxId,
    String boxName,
    BigDecimal amount,
    int lineOrder
) {}
