package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.CreditMsiPlan;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface CreditMsiPlanRepository {
    List<CreditMsiPlan> findByAccountId(Long accountId);
    boolean hasActiveByAccountId(Long accountId);
    boolean existsByTransactionId(Long transactionId);
    CreditMsiPlan save(CreditMsiPlan plan);
    Optional<CreditMsiPlan> findById(Long id);
    CreditMsiPlan end(Long id, LocalDateTime endedAt, String endReason);
    BigDecimal statementBalanceAdjustment(Long accountId, java.time.LocalDate periodEnd);
}
