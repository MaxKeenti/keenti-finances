package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxHistoryEntry;
import com.keenti.finances.domain.model.BoxLedger;
import com.keenti.finances.domain.model.BoxMovement;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@UserScoped
public class PanacheBoxRepository implements BoxRepository {

    private static final String BALANCE_SQL = """
        SELECT COALESCE(SUM(entry.delta), 0)
        FROM (
            SELECT destination_box_id AS box_id, amount AS delta
            FROM box_movement
            WHERE destination_box_id = :boxId
            UNION ALL
            SELECT source_box_id AS box_id, -amount AS delta
            FROM box_movement
            WHERE source_box_id = :boxId
            UNION ALL
            SELECT funding.box_id, -funding.amount AS delta
            FROM box_funding funding
            JOIN transaction tx ON tx.id = funding.transaction_id
            WHERE funding.box_id = :boxId
              AND funding.user_id = :userId
              AND tx.user_id = :userId
              AND tx.deleted_at IS NULL
        ) entry
        WHERE EXISTS (
            SELECT 1 FROM box owned
            WHERE owned.id = :boxId AND owned.user_id = :userId
        )
        """;

    private static final String TOTAL_BALANCE_SQL = """
        SELECT COALESCE(SUM(entry.delta), 0)
        FROM (
            SELECT movement.destination_box_id AS box_id, movement.amount AS delta
            FROM box_movement movement
            UNION ALL
            SELECT movement.source_box_id AS box_id, -movement.amount AS delta
            FROM box_movement movement
            UNION ALL
            SELECT funding.box_id, -funding.amount AS delta
            FROM box_funding funding
            JOIN transaction tx ON tx.id = funding.transaction_id
            WHERE funding.user_id = :userId
              AND tx.user_id = :userId
              AND tx.deleted_at IS NULL
        ) entry
        JOIN box owned ON owned.id = entry.box_id
        WHERE owned.user_id = :userId
          AND owned.archived = FALSE
        """;

    private static final String HISTORY_SQL = """
        SELECT
            movement.id,
            CASE
                WHEN movement.movement_type = 'DEPOSIT' THEN 'DEPOSIT'
                WHEN movement.movement_type = 'WITHDRAWAL' THEN 'WITHDRAWAL'
                WHEN movement.destination_box_id = :boxId THEN 'TRANSFER_IN'
                ELSE 'TRANSFER_OUT'
            END AS history_type,
            movement.amount,
            movement.effective_date,
            movement.created_at,
            CASE
                WHEN movement.movement_type = 'TRANSFER'
                     AND movement.destination_box_id = :boxId
                    THEN movement.source_box_id
                WHEN movement.movement_type = 'TRANSFER'
                    THEN movement.destination_box_id
                ELSE NULL
            END AS related_box_id,
            related.name AS related_box_name,
            movement.source_transaction_reference AS related_transaction_id,
            source_tx.description AS related_transaction_description,
            movement.source_transaction_changed AS related_transaction_changed,
            CASE
                WHEN movement.source_transaction_reference IS NULL THEN FALSE
                WHEN source_tx.id IS NULL OR source_tx.deleted_at IS NOT NULL THEN TRUE
                ELSE FALSE
            END AS related_transaction_removed
        FROM box_movement movement
        LEFT JOIN box related
          ON related.id = CASE
                WHEN movement.movement_type = 'TRANSFER'
                     AND movement.destination_box_id = :boxId
                    THEN movement.source_box_id
                WHEN movement.movement_type = 'TRANSFER'
                    THEN movement.destination_box_id
                ELSE NULL
             END
         AND related.user_id = :userId
        LEFT JOIN transaction source_tx
          ON source_tx.id = movement.source_transaction_id
         AND source_tx.user_id = :userId
        WHERE (movement.source_box_id = :boxId
               OR movement.destination_box_id = :boxId)
          AND EXISTS (
              SELECT 1 FROM box owned
              WHERE owned.id = :boxId AND owned.user_id = :userId
          )

        UNION ALL

        SELECT
            funding.id,
            'SPENDING' AS history_type,
            funding.amount,
            funding.effective_date,
            funding.created_at,
            NULL::BIGINT AS related_box_id,
            NULL::VARCHAR AS related_box_name,
            funding.transaction_id AS related_transaction_id,
            tx.description AS related_transaction_description,
            FALSE AS related_transaction_changed,
            FALSE AS related_transaction_removed
        FROM box_funding funding
        JOIN transaction tx ON tx.id = funding.transaction_id
        WHERE funding.box_id = :boxId
          AND funding.user_id = :userId
          AND tx.user_id = :userId
          AND tx.deleted_at IS NULL
          AND EXISTS (
              SELECT 1 FROM box owned
              WHERE owned.id = :boxId AND owned.user_id = :userId
          )
        """;

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public List<Box> findAll(boolean archived) {
        return BoxEntity.<BoxEntity>find(
                "archived = ?1 ORDER BY displayOrder, id", archived)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<Box> findActiveById(Long id) {
        return BoxEntity.<BoxEntity>find("id = ?1 AND archived = false", id)
            .firstResultOptional()
            .map(this::toDomain);
    }

    @Override
    public Optional<Box> findByIdIncludingArchived(Long id) {
        return BoxEntity.<BoxEntity>find("id = ?1", id)
            .firstResultOptional()
            .map(this::toDomain);
    }

    @Override
    public Optional<Box> lockActiveById(Long id) {
        return lockEntity(id, false).map(this::toDomain);
    }

    @Override
    public Optional<Box> lockByIdIncludingArchived(Long id) {
        return lockEntity(id, null).map(this::toDomain);
    }

    @Override
    public Box save(Box box) {
        LocalDateTime now = LocalDateTime.now();
        BoxEntity entity = new BoxEntity();
        entity.user = UserEntity.findById(userContext.getUserId());
        entity.name = box.getName();
        entity.hue = box.getHue();
        entity.icon = box.getIcon();
        entity.description = box.getDescription();
        entity.displayOrder = box.getDisplayOrder();
        entity.archived = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.persist();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public Box update(Box box) {
        BoxEntity entity = BoxEntity.<BoxEntity>find(
                "id = ?1 AND archived = false", box.getId())
            .firstResult();
        entity.name = box.getName();
        entity.hue = box.getHue();
        entity.icon = box.getIcon();
        entity.description = box.getDescription();
        entity.updatedAt = LocalDateTime.now();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public List<Box> reorder(List<Long> boxIds) {
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < boxIds.size(); index++) {
            BoxEntity entity = BoxEntity.<BoxEntity>find(
                    "id = ?1 AND archived = false", boxIds.get(index))
                .firstResult();
            entity.displayOrder = index;
            entity.updatedAt = now;
        }
        em.flush();
        return findAll(false);
    }

    @Override
    public Box setArchived(Long id, boolean archived, int displayOrder) {
        BoxEntity entity = BoxEntity.findById(id);
        entity.archived = archived;
        entity.displayOrder = displayOrder;
        entity.updatedAt = LocalDateTime.now();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public boolean existsActiveByName(String name, Long excludingId) {
        if (excludingId == null) {
            return BoxEntity.count(
                "LOWER(name) = LOWER(?1) AND archived = false", name) > 0;
        }
        return BoxEntity.count(
            "LOWER(name) = LOWER(?1) AND archived = false AND id <> ?2",
            name, excludingId) > 0;
    }

    @Override
    public int nextDisplayOrder() {
        Integer maximum = em.createQuery(
                "SELECT MAX(box.displayOrder) FROM BoxEntity box WHERE box.archived = false",
                Integer.class)
            .getSingleResult();
        return maximum == null ? 0 : maximum + 1;
    }

    @Override
    public void lockAllocationScope() {
        em.find(UserEntity.class, userContext.getUserId(), LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public BoxMovement saveMovement(BoxMovement movement) {
        BoxMovementEntity entity = new BoxMovementEntity();
        entity.movementType = movement.type().name();
        entity.sourceBox = movement.sourceBoxId() == null
            ? null
            : em.getReference(BoxEntity.class, movement.sourceBoxId());
        entity.destinationBox = movement.destinationBoxId() == null
            ? null
            : em.getReference(BoxEntity.class, movement.destinationBoxId());
        entity.amount = movement.amount();
        entity.effectiveDate = movement.effectiveDate();
        entity.createdAt = movement.createdAt();
        entity.sourceTransaction = movement.sourceTransactionId() == null
            ? null
            : em.getReference(TransactionEntity.class, movement.sourceTransactionId());
        entity.sourceTransactionReference = movement.sourceTransactionReference();
        entity.sourceTransactionOrder = movement.sourceTransactionOrder();
        entity.sourceTransactionChanged = movement.sourceTransactionChanged();
        entity.persist();

        touch(entity.sourceBox, movement.createdAt());
        if (entity.destinationBox != entity.sourceBox) {
            touch(entity.destinationBox, movement.createdAt());
        }
        em.flush();
        return toDomain(entity);
    }

    @Override
    public Optional<BoxMovement> lockMovementByIdForBox(
            Long movementId, Long boxId) {
        return em.createQuery("""
                SELECT movement
                FROM BoxMovementEntity movement
                LEFT JOIN movement.sourceBox source
                LEFT JOIN movement.destinationBox destination
                WHERE movement.id = :movementId
                  AND (source.id = :boxId OR destination.id = :boxId)
                  AND (source IS NULL OR source.user.id = :userId)
                  AND (destination IS NULL OR destination.user.id = :userId)
                """, BoxMovementEntity.class)
            .setParameter("movementId", movementId)
            .setParameter("boxId", boxId)
            .setParameter("userId", userContext.getUserId())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList().stream()
            .findFirst()
            .map(this::toDomain);
    }

    @Override
    public BoxMovement updateMovement(BoxMovement movement) {
        BoxMovementEntity entity = em.createQuery("""
                SELECT candidate
                FROM BoxMovementEntity candidate
                LEFT JOIN candidate.sourceBox source
                LEFT JOIN candidate.destinationBox destination
                WHERE candidate.id = :movementId
                  AND (source IS NULL OR source.user.id = :userId)
                  AND (destination IS NULL OR destination.user.id = :userId)
                """, BoxMovementEntity.class)
            .setParameter("movementId", movement.id())
            .setParameter("userId", userContext.getUserId())
            .getResultList().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Caller-scoped Box Movement disappeared"));
        entity.amount = movement.amount();
        entity.effectiveDate = movement.effectiveDate();
        LocalDateTime changedAt = LocalDateTime.now();
        touch(entity.sourceBox, changedAt);
        if (entity.destinationBox != entity.sourceBox) {
            touch(entity.destinationBox, changedAt);
        }
        em.flush();
        return toDomain(entity);
    }

    @Override
    public boolean replacementRemainsNonNegative(BoxMovement movement) {
        List<Long> affectedBoxIds = java.util.stream.Stream.of(
                movement.sourceBoxId(), movement.destinationBoxId())
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        for (Long boxId : affectedBoxIds) {
            List<BoxHistoryEntry> candidate = new ArrayList<>(
                findHistoryExcludingMovement(boxId, movement.id()));
            candidate.add(replacementHistoryEntry(movement, boxId));
            if (!BoxLedger.remainsNonNegative(candidate)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<BoxHistoryEntry> findHistory(Long boxId) {
        return findHistory(boxId, null);
    }

    @SuppressWarnings("unchecked")
    private List<BoxHistoryEntry> findHistory(
            Long boxId, Long excludedMovementId) {
        String sql = excludedMovementId == null
            ? HISTORY_SQL
            : HISTORY_SQL.replace(
                "WHERE (movement.source_box_id = :boxId",
                "WHERE movement.id <> :excludedMovementId\n"
                    + "          AND (movement.source_box_id = :boxId");
        var query = em.createNativeQuery(sql)
            .setParameter("boxId", boxId)
            .setParameter("userId", userContext.getUserId());
        if (excludedMovementId != null) {
            query.setParameter("excludedMovementId", excludedMovementId);
        }
        List<Object[]> rows = query.getResultList();

        List<BoxHistoryEntry> raw = rows.stream()
            .map(this::toHistoryEntry)
            .toList();
        List<BoxHistoryEntry> chronological = BoxLedger.withRunningBalances(raw);
        List<BoxHistoryEntry> newestFirst = new ArrayList<>(chronological);
        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    private List<BoxHistoryEntry> findHistoryExcludingMovement(
            Long boxId, Long movementId) {
        return findHistory(boxId, movementId);
    }

    private BoxHistoryEntry replacementHistoryEntry(
            BoxMovement movement, Long boxId) {
        BoxHistoryEntry.Type type;
        if (movement.type() == BoxMovement.Type.DEPOSIT) {
            type = BoxHistoryEntry.Type.DEPOSIT;
        } else if (movement.type() == BoxMovement.Type.WITHDRAWAL) {
            type = BoxHistoryEntry.Type.WITHDRAWAL;
        } else if (boxId.equals(movement.destinationBoxId())) {
            type = BoxHistoryEntry.Type.TRANSFER_IN;
        } else {
            type = BoxHistoryEntry.Type.TRANSFER_OUT;
        }
        return new BoxHistoryEntry(
            movement.id(), type, movement.amount(), movement.effectiveDate(),
            movement.createdAt(), null, null, null, null, null,
            movement.sourceTransactionChanged(), false);
    }

    @Override
    public BigDecimal getBalance(Long boxId) {
        Object raw = em.createNativeQuery(BALANCE_SQL)
            .setParameter("boxId", boxId)
            .setParameter("userId", userContext.getUserId())
            .getSingleResult();
        return decimal(raw);
    }

    @Override
    public BigDecimal getTotalBalance() {
        Object raw = em.createNativeQuery(TOTAL_BALANCE_SQL)
            .setParameter("userId", userContext.getUserId())
            .getSingleResult();
        return decimal(raw);
    }

    @Override
    public boolean canDebit(Long boxId, BigDecimal amount, LocalDate effectiveDate,
                            LocalDateTime createdAt) {
        List<BoxHistoryEntry> entries = findHistory(boxId);
        return BoxLedger.canApplyDebit(entries, amount, effectiveDate, createdAt);
    }

    private Optional<BoxEntity> lockEntity(Long id, Boolean archived) {
        String condition = archived == null
            ? "box.id = :id"
            : "box.id = :id AND box.archived = :archived";
        var query = em.createQuery(
                "SELECT box FROM BoxEntity box WHERE " + condition,
                BoxEntity.class)
            .setParameter("id", id)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE);
        if (archived != null) {
            query.setParameter("archived", archived);
        }
        return query.getResultStream().findFirst();
    }

    private void touch(BoxEntity entity, LocalDateTime changedAt) {
        if (entity != null) {
            entity.updatedAt = changedAt;
        }
    }

    private Box toDomain(BoxEntity entity) {
        return new Box(
            entity.id,
            entity.name,
            entity.hue,
            entity.icon,
            entity.description,
            entity.displayOrder,
            getBalance(entity.id),
            entity.archived,
            entity.createdAt,
            entity.updatedAt,
            entity.version
        );
    }

    private BoxMovement toDomain(BoxMovementEntity entity) {
        return new BoxMovement(
            entity.id,
            BoxMovement.Type.valueOf(entity.movementType),
            entity.sourceBox == null ? null : entity.sourceBox.id,
            entity.destinationBox == null ? null : entity.destinationBox.id,
            entity.amount,
            entity.effectiveDate,
            entity.createdAt,
            entity.sourceTransaction == null ? null : entity.sourceTransaction.id,
            entity.sourceTransactionReference,
            entity.sourceTransactionOrder,
            entity.sourceTransactionChanged
        );
    }

    private BoxHistoryEntry toHistoryEntry(Object[] row) {
        return new BoxHistoryEntry(
            longValue(row[0]),
            BoxHistoryEntry.Type.valueOf((String) row[1]),
            decimal(row[2]),
            localDate(row[3]),
            localDateTime(row[4]),
            null,
            longValue(row[5]),
            (String) row[6],
            longValue(row[7]),
            (String) row[8],
            booleanValue(row[9]),
            booleanValue(row[10])
        );
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal
            ? decimal
            : new BigDecimal(value.toString());
    }

    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((Date) value).toLocalDate();
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool
            ? bool
            : Boolean.parseBoolean(value.toString());
    }
}
