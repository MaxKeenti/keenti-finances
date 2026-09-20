package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtRequest(
    @NotNull Long contactId,
    String direction,
    @NotBlank String description,
    @NotNull @Positive BigDecimal totalAmount,
    LocalDate createdAt
) {

    /**
     * Debts predate the bidirectional Direction, so an omitted one still means
     * what every Debt used to mean: money owed to the User. The value itself is
     * validated by the use case, not here.
     */
    public String directionOrDefault() {
        return direction == null || direction.isBlank() ? "INGRESS" : direction;
    }
}
