package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.MonthSummary;
import com.keenti.finances.domain.model.PagedResult;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.model.Transaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    List<Transaction> findAll();
    PagedResult<Transaction> findPage(int pageIndex, int pageSize, String sortBy, boolean descending);
    Optional<Transaction> findById(Long id);
    Transaction save(Transaction transaction);
    Transaction update(Transaction transaction);
    void deleteById(Long id);
    List<MonthSummary> findMonthlySummary(int year, String currency);
    /** All-time INGRESS minus EGRESS in one currency (ADR-0020's pre-activation Net Balance). */
    BigDecimal getNetBalance(String currency);
    List<Transaction> findBySubscriptionId(Long subscriptionId);
    void softDeleteById(Long id);
    void restoreById(Long id);
    Optional<Transaction> findDeletedTransactionById(Long id);
    Optional<TrashItem> findDeletedById(Long id);
    List<TrashItem> findAllDeleted();
}
