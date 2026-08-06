package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public record CreditAccountSettings(
    Long accountId,
    BigDecimal creditLimit,
    int statementClosingDay,
    int paymentDueDay
) {}
