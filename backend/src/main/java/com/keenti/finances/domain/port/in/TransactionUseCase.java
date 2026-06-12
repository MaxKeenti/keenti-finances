package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.PagedResult;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.model.Transaction;
import java.util.List;
import java.util.Optional;

public interface TransactionUseCase {
    List<Transaction> list();
    PagedResult<Transaction> listPage(int pageIndex, int pageSize, String sortBy, boolean descending);
    Optional<Transaction> getById(Long id);
    Transaction create(Transaction transaction);
    Transaction update(Long id, Transaction transaction);
    void delete(Long id);
    Transaction linkSubscription(Long transactionId, Long subscriptionId);
    List<Transaction> listBySubscriptionId(Long subscriptionId);
    void restore(Long id);
    void permanentDelete(Long id);
    List<TrashItem> listDeleted();
}
