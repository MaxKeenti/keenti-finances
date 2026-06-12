package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PagedResult;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.in.TransactionUseCase;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import com.keenti.finances.domain.port.out.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TransactionService implements TransactionUseCase {

    private static final Logger LOG = Logger.getLogger(TransactionService.class);
    private static final Set<String> VALID_DIRECTIONS = Set.of("INGRESS", "EGRESS");

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Override
    public List<Transaction> list() {
        List<Transaction> transactions = transactionRepository.findAll();
        LOG.infof("transaction.list count=%d", transactions.size());
        return transactions;
    }

    @Override
    public PagedResult<Transaction> listPage(int pageIndex, int pageSize, String sortBy, boolean descending) {
        PagedResult<Transaction> page = transactionRepository.findPage(pageIndex, pageSize, sortBy, descending);
        LOG.infof(
            "transaction.listPage pageIndex=%d pageSize=%d sortBy=%s descending=%b total=%d",
            pageIndex, pageSize, sortBy, descending, page.totalItems()
        );
        return page;
    }

    @Override
    public Optional<Transaction> getById(Long id) {
        Optional<Transaction> result = transactionRepository.findById(id);
        LOG.infof("transaction.get id=%d found=%b", id, result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public Transaction create(Transaction transaction) {
        if (!VALID_DIRECTIONS.contains(transaction.getDirection())) {
            throw new BadRequestException("Invalid direction: " + transaction.getDirection() + ". Must be INGRESS or EGRESS");
        }
        Transaction created = transactionRepository.save(transaction);
        LOG.infof("transaction.create id=%d amount=%s direction=%s", created.getId(), created.getAmount(), created.getDirection());
        return created;
    }

    @Override
    @Transactional
    public Transaction update(Long id, Transaction transaction) {
        if (!VALID_DIRECTIONS.contains(transaction.getDirection())) {
            throw new BadRequestException("Invalid direction: " + transaction.getDirection() + ". Must be INGRESS or EGRESS");
        }
        transactionRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Transaction not found: " + id));
        Transaction updated = transactionRepository.update(new Transaction(
            id, transaction.getAmount(), transaction.getDirection(), transaction.getDescription(),
            transaction.getTransactionDate(), transaction.getCategoryId(), transaction.getContactId(),
            transaction.getSubscriptionId()));
        LOG.infof("transaction.update id=%d", id);
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        transactionRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Transaction not found: " + id));
        transactionRepository.softDeleteById(id);
        LOG.infof("transaction.soft_deleted id=%d", id);
    }

    @Override
    @Transactional
    public void restore(Long id) {
        transactionRepository.findDeletedById(id).orElseThrow(() ->
            new NotFoundException("Deleted transaction not found: " + id));
        transactionRepository.restoreById(id);
        LOG.infof("transaction.restored id=%d", id);
    }

    @Override
    @Transactional
    public void permanentDelete(Long id) {
        transactionRepository.findDeletedById(id).orElseThrow(() ->
            new NotFoundException("Deleted transaction not found: " + id));
        transactionRepository.deleteById(id);
        LOG.infof("transaction.permanent_deleted id=%d", id);
    }

    @Override
    public List<TrashItem> listDeleted() {
        List<TrashItem> items = transactionRepository.findAllDeleted();
        LOG.infof("transaction.trash.list count=%d", items.size());
        return items;
    }

    @Override
    @Transactional
    public Transaction linkSubscription(Long transactionId, Long subscriptionId) {
        Transaction existing = transactionRepository.findById(transactionId).orElseThrow(() ->
            new NotFoundException("Transaction not found: " + transactionId));
        if (subscriptionId != null) {
            subscriptionRepository.findById(subscriptionId).orElseThrow(() ->
                new NotFoundException("Subscription not found: " + subscriptionId));
        }
        Transaction updated = transactionRepository.update(new Transaction(
            existing.getId(), existing.getAmount(), existing.getDirection(), existing.getDescription(),
            existing.getTransactionDate(), existing.getCategoryId(), existing.getContactId(),
            subscriptionId));
        LOG.infof("transaction.link transactionId=%d subscriptionId=%s", transactionId,
            subscriptionId != null ? subscriptionId.toString() : "null");
        return updated;
    }

    @Override
    public List<Transaction> listBySubscriptionId(Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId).orElseThrow(() ->
            new NotFoundException("Subscription not found: " + subscriptionId));
        List<Transaction> transactions = transactionRepository.findBySubscriptionId(subscriptionId);
        LOG.infof("transaction.listBySubscription subscriptionId=%d count=%d", subscriptionId, transactions.size());
        return transactions;
    }
}
