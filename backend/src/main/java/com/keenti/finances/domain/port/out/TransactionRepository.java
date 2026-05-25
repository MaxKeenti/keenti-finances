package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.MonthSummary;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.model.Transaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    List<Transaction> findAll();
    Optional<Transaction> findById(Long id);
    Transaction save(Transaction transaction);
    Transaction update(Transaction transaction);
    void deleteById(Long id);
    List<MonthSummary> findMonthlySummary(int year);
    BigDecimal getNetBalance();
    List<Transaction> findBySubscriptionId(Long subscriptionId);
    void softDeleteById(Long id);
    void restoreById(Long id);
    Optional<TrashItem> findDeletedById(Long id);
    List<TrashItem> findAllDeleted();
}
