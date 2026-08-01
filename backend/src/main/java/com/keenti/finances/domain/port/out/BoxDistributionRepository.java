package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.BoxDistribution;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BoxDistributionRepository {

    List<BoxDistribution> findByTransactionId(Long transactionId);

    List<BoxDistribution> saveForTransaction(Long transactionId, LocalDate effectiveDate,
                                             LocalDateTime createdAt,
                                             List<BoxDistribution> distributions);

    void markSourceChanged(Long transactionId);
}
