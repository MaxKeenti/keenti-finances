package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.CreditStatement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CreditStatementUseCase {
    List<CreditStatement> list(Long accountId);
    BigDecimal estimateOutstandingBalance(Long accountId, LocalDate periodEnd);
    CreditStatement confirm(CreditStatement statement);
    CreditStatement reconfirm(Long statementId, CreditStatement statement);
}
