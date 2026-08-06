package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;

public record CreditAccountSettingsResponse(Long accountId, BigDecimal creditLimit,
                                            int statementClosingDay, int paymentDueDay) {}
