package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DebtPaymentRequest(
    @NotNull @Positive BigDecimal amount,
    @NotNull LocalDate paymentDate,
    @NotNull Long categoryId,
    Long accountId,
    String notes,
    /**
     * Boxes this payment is drawn from. Only a Debt the User owes can carry
     * these: the Transaction it creates is the EGRESS the funding applies to.
     */
    List<@Valid BoxFundingRequest> boxFunding
) {

    /** Omitting the funding means the payment comes wholly from Available to Spend. */
    public List<BoxFundingRequest> boxFundingOrEmpty() {
        return boxFunding == null ? List.of() : boxFunding;
    }
}
