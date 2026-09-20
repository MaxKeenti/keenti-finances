package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BulkPaymentRequest(
    @NotNull Long contactId,
    String direction,
    @NotNull @Positive BigDecimal totalAmount,
    @NotNull LocalDate paymentDate,
    @NotNull Long categoryId,
    Long accountId,
    String notes
) {

    /** Omitting the Direction settles what the Contact owes the User, as it always did. */
    public String directionOrDefault() {
        return direction == null || direction.isBlank() ? "INGRESS" : direction;
    }
}
