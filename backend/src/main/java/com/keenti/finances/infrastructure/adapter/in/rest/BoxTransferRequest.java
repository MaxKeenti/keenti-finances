package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BoxTransferRequest(
    @NotNull Long targetBoxId,
    @NotNull @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 10, fraction = 2) BigDecimal amount,
    @NotNull LocalDate effectiveDate
) {}
