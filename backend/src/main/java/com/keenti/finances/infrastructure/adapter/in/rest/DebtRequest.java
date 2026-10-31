package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtRequest(
    @NotNull Long contactId,
    String direction,
    /** Defaults to the Primary Currency on create and to the Debt's own on update. */
    @Size(max = 3) String currency,
    @NotBlank String description,
    @NotNull @Positive BigDecimal totalAmount,
    LocalDate createdAt
) {

    /**
     * Debts predate the bidirectional Direction, so an omitted one still means
     * what every Debt used to mean: money owed to the User. The value itself is
     * validated by the use case, not here.
     */
    public DebtRequest(Long contactId, String direction, String description, BigDecimal totalAmount,
                       LocalDate createdAt) {
        this(contactId, direction, null, description, totalAmount, createdAt);
    }

    public String directionOrDefault() {
        return direction == null || direction.isBlank() ? "INGRESS" : direction;
    }
}
