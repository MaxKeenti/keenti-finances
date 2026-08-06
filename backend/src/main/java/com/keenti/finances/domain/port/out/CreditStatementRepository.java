package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.CreditStatement;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CreditStatementRepository {
    List<CreditStatement> findByAccountId(Long accountId);
    Optional<CreditStatement> findByAccountIdAndPeriod(Long accountId, LocalDate periodStart,
                                                        LocalDate periodEnd);
    Optional<CreditStatement> findById(Long id);
    CreditStatement save(CreditStatement statement);
    CreditStatement updateOfficialFigures(CreditStatement statement);
    void saveRevision(CreditStatement statement);
    long revisionCount(Long statementId);
    void allocateOldestOutstanding(Long accountId, Long transferId, LocalDate paymentDate,
                                   BigDecimal amount);
    void removeAllocationsForTransfer(Long transferId);
}
