package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.MonthSummary;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.out.TransactionRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheTransactionRepository implements TransactionRepository {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public List<Transaction> findAll() {
        return TransactionEntity.<TransactionEntity>find(
                "ORDER BY transactionDate DESC, createdAt DESC")
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return TransactionEntity.<TransactionEntity>findByIdOptional(id)
                .map(this::toDomain);
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = toEntity(transaction);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public Transaction update(Transaction transaction) {
        TransactionEntity entity = TransactionEntity.findById(transaction.getId());
        entity.amount = transaction.getAmount();
        entity.direction = transaction.getDirection();
        entity.description = transaction.getDescription();
        entity.transactionDate = transaction.getTransactionDate();
        entity.category = CategoryEntity.findById(transaction.getCategoryId());
        entity.contact = transaction.getContactId() != null
                ? ContactEntity.findById(transaction.getContactId())
                : null;
        entity.subscription = transaction.getSubscriptionId() != null
                ? SubscriptionEntity.findById(transaction.getSubscriptionId())
                : null;
        entity.user = UserEntity.findById(userContext.getUserId());
        return toDomain(entity);
    }

    @Override
    public void deleteById(Long id) {
        TransactionEntity.deleteById(id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MonthSummary> findMonthlySummary(int year) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT EXTRACT(MONTH FROM transaction_date) AS month, direction, SUM(amount) AS total " +
            "FROM transaction " +
            "WHERE EXTRACT(YEAR FROM transaction_date) = :year AND user_id = :userId " +
            "GROUP BY EXTRACT(MONTH FROM transaction_date), direction " +
            "ORDER BY EXTRACT(MONTH FROM transaction_date)")
            .setParameter("year", year)
            .setParameter("userId", userContext.getUserId())
            .getResultList();

        Map<Integer, BigDecimal[]> byMonth = new HashMap<>();
        for (Object[] row : rows) {
            int month = ((Number) row[0]).intValue();
            String direction = (String) row[1];
            BigDecimal total = (BigDecimal) row[2];
            byMonth.computeIfAbsent(month, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("INGRESS".equals(direction)) {
                byMonth.get(month)[0] = total;
            } else {
                byMonth.get(month)[1] = total;
            }
        }

        List<MonthSummary> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            BigDecimal[] amounts = byMonth.getOrDefault(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            result.add(new MonthSummary(m, amounts[0], amounts[1]));
        }
        return result;
    }

    @Override
    public BigDecimal getNetBalance() {
        Object raw = em.createNativeQuery(
            "SELECT COALESCE(SUM(CASE WHEN direction='INGRESS' THEN amount ELSE -amount END), 0) FROM transaction " +
            "WHERE user_id = :userId")
            .setParameter("userId", userContext.getUserId())
            .getSingleResult();
        return raw instanceof BigDecimal ? (BigDecimal) raw : new BigDecimal(raw.toString());
    }

    @Override
    public List<Transaction> findBySubscriptionId(Long subscriptionId) {
        return TransactionEntity.<TransactionEntity>find(
                "subscription.id = ?1 ORDER BY transactionDate DESC, createdAt DESC", subscriptionId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private TransactionEntity toEntity(Transaction t) {
        TransactionEntity e = new TransactionEntity();
        e.amount = t.getAmount();
        e.direction = t.getDirection();
        e.description = t.getDescription();
        e.transactionDate = t.getTransactionDate();
        e.category = CategoryEntity.findById(t.getCategoryId());
        e.contact = t.getContactId() != null
                ? ContactEntity.findById(t.getContactId())
                : null;
        e.subscription = t.getSubscriptionId() != null
                ? SubscriptionEntity.findById(t.getSubscriptionId())
                : null;
        e.user = UserEntity.findById(userContext.getUserId());
        return e;
    }

    private Transaction toDomain(TransactionEntity e) {
        return new Transaction(
            e.id,
            e.amount,
            e.direction,
            e.description,
            e.transactionDate,
            e.category != null ? e.category.id : null,
            e.contact != null ? e.contact.id : null,
            e.subscription != null ? e.subscription.id : null
        );
    }
}
