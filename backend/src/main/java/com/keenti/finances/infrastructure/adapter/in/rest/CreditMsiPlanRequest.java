package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreditMsiPlanRequest(@NotNull Long transactionId, @NotNull @Min(2) @Max(60) Integer installmentCount,
                                   @NotNull LocalDate firstInstallmentDate) {}
