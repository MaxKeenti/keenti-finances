package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.BoxDistribution;
import com.keenti.finances.domain.model.BoxMovement;
import com.keenti.finances.domain.port.out.BoxDistributionRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@UserScoped
public class PanacheBoxDistributionRepository implements BoxDistributionRepository {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Inject
    BoxRepository boxRepository;

    @Override
    public List<BoxDistribution> findByTransactionId(Long transactionId) {
        return em.createQuery("""
                SELECT movement
                FROM BoxMovementEntity movement
                JOIN FETCH movement.destinationBox destination
                WHERE movement.sourceTransactionReference = :transactionId
                  AND destination.user.id = :userId
                ORDER BY movement.sourceTransactionOrder, movement.id
                """, BoxMovementEntity.class)
            .setParameter("transactionId", transactionId)
            .setParameter("userId", userContext.getUserId())
            .getResultStream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<BoxDistribution> saveForTransaction(
            Long transactionId, LocalDate effectiveDate, LocalDateTime createdAt,
            List<BoxDistribution> distributions) {
        List<BoxDistribution> saved = new ArrayList<>(distributions.size());
        for (BoxDistribution distribution : distributions) {
            BoxMovement movement = boxRepository.saveMovement(new BoxMovement(
                null,
                BoxMovement.Type.DEPOSIT,
                null,
                distribution.boxId(),
                distribution.amount(),
                effectiveDate,
                createdAt,
                transactionId,
                transactionId,
                distribution.lineOrder(),
                false
            ));
            saved.add(new BoxDistribution(
                movement.id(),
                transactionId,
                distribution.boxId(),
                boxName(distribution.boxId()),
                distribution.amount(),
                distribution.lineOrder(),
                effectiveDate,
                createdAt
            ));
        }
        return List.copyOf(saved);
    }

    @Override
    public void markSourceChanged(Long transactionId) {
        em.createNativeQuery("""
                UPDATE box_movement movement
                SET source_transaction_changed = TRUE
                FROM box destination
                WHERE movement.destination_box_id = destination.id
                  AND movement.source_transaction_reference = :transactionId
                  AND destination.user_id = :userId
                """)
            .setParameter("transactionId", transactionId)
            .setParameter("userId", userContext.getUserId())
            .executeUpdate();
    }

    private BoxDistribution toDomain(BoxMovementEntity movement) {
        return new BoxDistribution(
            movement.id,
            movement.sourceTransactionReference,
            movement.destinationBox.id,
            movement.destinationBox.name,
            movement.amount,
            movement.sourceTransactionOrder,
            movement.effectiveDate,
            movement.createdAt
        );
    }

    private String boxName(Long boxId) {
        return BoxEntity.<BoxEntity>find("id = ?1", boxId)
            .firstResultOptional()
            .map(box -> box.name)
            .orElse(null);
    }
}
