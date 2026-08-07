package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreditMsiPlan(Long id, Long accountId, Long transactionId, BigDecimal purchaseAmount,
                            int installmentCount, LocalDate firstInstallmentDate,
                            BigDecimal openingBalanceAmount, LocalDateTime endedAt,
                            String endReason) {
    public BigDecimal installmentAmount() {
        return purchaseAmount.divide(BigDecimal.valueOf(installmentCount));
    }

    public boolean active() {
        return endedAt == null;
    }
}
