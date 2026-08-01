package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.BoxFunding;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BoxFundingRepository {

    List<BoxFunding> findByTransactionId(Long transactionId);

    void deleteForTransaction(Long transactionId);

    void saveForTransaction(Long transactionId, LocalDate effectiveDate,
                            LocalDateTime createdAt, List<BoxFunding> funding);

    void flush();
}
