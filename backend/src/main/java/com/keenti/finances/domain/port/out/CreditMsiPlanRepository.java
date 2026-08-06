package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.CreditMsiPlan;
import java.util.List;

public interface CreditMsiPlanRepository {
    List<CreditMsiPlan> findByAccountId(Long accountId);
    boolean hasActiveByAccountId(Long accountId);
    boolean existsByTransactionId(Long transactionId);
    CreditMsiPlan save(CreditMsiPlan plan);
}
