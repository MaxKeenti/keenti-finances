package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditMsiPlan(Long id, Long accountId, Long transactionId, BigDecimal purchaseAmount,
                            int installmentCount, LocalDate firstInstallmentDate, boolean active) {
    public BigDecimal installmentAmount() {
        return purchaseAmount.divide(BigDecimal.valueOf(installmentCount));
    }
}
