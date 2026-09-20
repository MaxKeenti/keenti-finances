package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.DebtPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface DebtPaymentRepository {
    List<DebtPayment> findByDebtId(Long debtId);
    DebtPayment save(DebtPayment payment);
    BigDecimal sumByDebtId(Long debtId);

    /**
     * Paid totals for several Debts in one query.
     *
     * <p>Summarizing every Debt one {@link #sumByDebtId} call at a time is two
     * round trips per Debt; the dashboard needs all of them at once. Debts with
     * no payments are absent from the result rather than mapped to zero, so a
     * caller decides for itself what "nothing paid" means.
     */
    Map<Long, BigDecimal> sumByDebtIds(List<Long> debtIds);
}
