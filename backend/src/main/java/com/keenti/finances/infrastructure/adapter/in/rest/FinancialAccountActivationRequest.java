package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record FinancialAccountActivationRequest(
    @NotNull LocalDate activationDate,
    @NotEmpty List<@Valid FinancialAccountRequest> accounts
) {}
