package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.MonthSummary;
import com.keenti.finances.domain.model.PagedResult;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.out.TransactionRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.persistence.HibernateSessions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.Session;

@ApplicationScoped
@com.keenti.finances.infrastructure.adapter.in.rest.UserScoped
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
                .toList();
    }

    @Override
    public PagedResult<Transaction> findPage(int pageIndex, int pageSize, String sortBy, boolean descending) {
        String orderBy = transactionOrderBy(sortBy, descending);
        long totalItems = em.createQuery("SELECT COUNT(t) FROM TransactionEntity t", Long.class)
                .getSingleResult();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil(totalItems / (double) pageSize);
        int effectivePageIndex = totalPages == 0 ? 0 : Math.min(pageIndex, totalPages - 1);

        List<Transaction> items = em.createQuery(
                    "SELECT t FROM TransactionEntity t " +
                    "LEFT JOIN t.category cat " +
                    "LEFT JOIN t.contact c " +
                    "ORDER BY " + orderBy,
                    TransactionEntity.class)
                .setFirstResult(effectivePageIndex * pageSize)
                .setMaxResults(pageSize)
                .getResultStream()
                .map(this::toDomain)
                .toList();
        return new PagedResult<>(items, effectivePageIndex, pageSize, totalItems, totalPages);
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return TransactionEntity.<TransactionEntity>find("id = ?1", id)
                .firstResultOptional()
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
            "WHERE EXTRACT(YEAR FROM transaction_date) = :year AND user_id = :userId AND deleted_at IS NULL " +
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
            "SELECT COALESCE(SUM(CASE WHEN direction='INGRESS' THEN amount ELSE -amount END), 0) " +
            "FROM transaction WHERE user_id = :userId AND deleted_at IS NULL")
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
                .toList();
    }

    @Override
    public void softDeleteById(Long id) {
        TransactionEntity.update("deletedAt = ?1 WHERE id = ?2", LocalDateTime.now(), id);
    }

    @Override
    public void restoreById(Long id) {
        Session session = HibernateSessions.unwrap(em);
        session.disableFilter("softDelete");
        try {
            TransactionEntity entity = TransactionEntity.findById(id);
            if (entity != null) {
                entity.deletedAt = null;
            }
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    @SuppressWarnings("null")
    public Optional<TrashItem> findDeletedById(Long id) {
        Session session = HibernateSessions.unwrap(em);
        session.disableFilter("softDelete");
        try {
            return TransactionEntity.<TransactionEntity>find(
                    "id = ?1 AND deletedAt IS NOT NULL", id)
                    .firstResultOptional()
                    .map(e -> new TrashItem(e.id, "transaction",
                            e.description != null ? e.description : e.amount.toPlainString(),
                            e.deletedAt));
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    @SuppressWarnings("null")
    public List<TrashItem> findAllDeleted() {
        Session session = HibernateSessions.unwrap(em);
        session.disableFilter("softDelete");
        try {
            return TransactionEntity.<TransactionEntity>find(
                    "deletedAt IS NOT NULL ORDER BY deletedAt DESC")
                    .stream()
                    .map(e -> new TrashItem(e.id, "transaction",
                            e.description != null ? e.description : e.amount.toPlainString(),
                            e.deletedAt))
                    .toList();
        } finally {
            session.enableFilter("softDelete");
        }
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

    private String transactionOrderBy(String sortBy, boolean descending) {
        String direction = descending ? "DESC" : "ASC";
        String expression = switch (sortBy) {
            case "amount" -> "t.amount";
            case "direction" -> "t.direction";
            case "description" -> "COALESCE(t.description, '')";
            case "categoryName" -> "cat.name";
            case "contactName" -> "COALESCE(c.name, '')";
            case "transactionDate" -> "t.transactionDate";
            default -> "t.transactionDate";
        };
        return expression + " " + direction + ", t.transactionDate DESC, t.createdAt DESC, t.id DESC";
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
