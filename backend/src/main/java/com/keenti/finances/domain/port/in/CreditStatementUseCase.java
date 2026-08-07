package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.model.CreditStatementEstimate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CreditStatementUseCase {
    List<CreditStatement> list(Long accountId);
    BigDecimal estimateOutstandingBalance(Long accountId, LocalDate periodEnd);
    CreditStatementEstimate estimateCurrentStatement(Long accountId, LocalDate today);
    CreditStatement confirm(CreditStatement statement);
    CreditStatement reconfirm(Long statementId, CreditStatement statement);
}
