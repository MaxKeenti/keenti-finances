package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.DebtPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DebtUseCase {
    List<Debt> list();
    Optional<Debt> getById(Long id);
    Debt create(Debt debt);
    Debt update(Long id, Debt debt);
    void delete(Long id);
    DebtPayment recordPayment(Long debtId, BigDecimal amount, LocalDate paymentDate, Long categoryId, String notes);
    List<DebtPayment> listPayments(Long debtId);
}
