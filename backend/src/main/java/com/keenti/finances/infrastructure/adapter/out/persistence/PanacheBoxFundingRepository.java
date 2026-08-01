package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.BoxFunding;
import com.keenti.finances.domain.port.out.BoxFundingRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
@UserScoped
public class PanacheBoxFundingRepository implements BoxFundingRepository {

    @Inject
    UserContext userContext;

    @Inject
    EntityManager em;

    @Override
    public List<BoxFunding> findByTransactionId(Long transactionId) {
        return BoxFundingEntity.<BoxFundingEntity>find(
                "transactionId = ?1 ORDER BY lineOrder", transactionId)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void deleteForTransaction(Long transactionId) {
        BoxFundingEntity.delete(
            "transactionId = ?1 AND userId = ?2", transactionId, userContext.getUserId());
    }

    @Override
    public void saveForTransaction(Long transactionId, LocalDate effectiveDate,
                                   LocalDateTime createdAt, List<BoxFunding> funding) {
        for (BoxFunding line : funding) {
            BoxFundingEntity entity = new BoxFundingEntity();
            entity.userId = userContext.getUserId();
            entity.transactionId = transactionId;
            entity.boxId = line.boxId();
            entity.amount = line.amount();
            entity.lineOrder = line.lineOrder();
            entity.effectiveDate = effectiveDate;
            entity.createdAt = createdAt;
            entity.persist();
        }
    }

    @Override
    public void flush() {
        em.flush();
    }

    private BoxFunding toDomain(BoxFundingEntity entity) {
        String boxName = BoxEntity.<BoxEntity>find("id = ?1", entity.boxId)
            .firstResultOptional()
            .map(box -> box.name)
            .orElse(null);
        return new BoxFunding(
            entity.id,
            entity.transactionId,
            entity.boxId,
            boxName,
            entity.amount,
            entity.lineOrder,
            entity.effectiveDate,
            entity.createdAt
        );
    }
}
