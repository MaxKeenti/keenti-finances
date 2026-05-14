package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.DebtPayment;
import java.math.BigDecimal;
import java.util.List;

public interface DebtPaymentRepository {
    List<DebtPayment> findByDebtId(Long debtId);
    DebtPayment save(DebtPayment payment);
    BigDecimal sumByDebtId(Long debtId);
}
