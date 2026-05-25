package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.out.DebtRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hibernate.Session;

@ApplicationScoped
public class PanacheDebtRepository implements DebtRepository {

    @Inject
    UserContext userContext;

    @Inject
    EntityManager em;

    @Override
    public List<Debt> findAll() {
        return DebtEntity.<DebtEntity>find("ORDER BY createdAt DESC")
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Debt> findById(Long id) {
        return DebtEntity.<DebtEntity>find("id = ?1", id)
                .firstResultOptional().map(this::toDomain);
    }

    @Override
    public List<Debt> findActiveByContactIdOrderByCreatedAt(Long contactId) {
        return DebtEntity.<DebtEntity>find(
                "contact.id = ?1 AND status = 'ACTIVE' ORDER BY createdAt ASC", contactId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Debt save(Debt debt) {
        DebtEntity entity = toEntity(debt);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public Debt update(Debt debt) {
        DebtEntity entity = DebtEntity.findById(debt.getId());
        entity.contact = ContactEntity.findById(debt.getContactId());
        entity.description = debt.getDescription();
        entity.totalAmount = debt.getTotalAmount();
        entity.status = debt.getStatus();
        if (debt.getCreatedAt() != null) {
            entity.createdAt = debt.getCreatedAt();
        }
        return toDomain(entity);
    }

    @Override
    public void deleteById(Long id) {
        DebtEntity.deleteById(id);
    }

    @Override
    public void softDeleteById(Long id) {
        DebtEntity.update("deletedAt = ?1 WHERE id = ?2", LocalDateTime.now(), id);
    }

    @Override
    public void restoreById(Long id) {
        Session session = em.unwrap(Session.class);
        session.disableFilter("softDelete");
        try {
            DebtEntity entity = DebtEntity.findById(id);
            if (entity != null) {
                entity.deletedAt = null;
            }
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    public Optional<TrashItem> findDeletedById(Long id) {
        Session session = em.unwrap(Session.class);
        session.disableFilter("softDelete");
        try {
            return DebtEntity.<DebtEntity>find(
                    "id = ?1 AND deletedAt IS NOT NULL", id)
                    .firstResultOptional()
                    .map(e -> new TrashItem(e.id, "debt", e.description, e.deletedAt));
        } finally {
            session.enableFilter("softDelete");
        }
    }

    @Override
    public List<TrashItem> findAllDeleted() {
        Session session = em.unwrap(Session.class);
        session.disableFilter("softDelete");
        try {
            return DebtEntity.<DebtEntity>find(
                    "deletedAt IS NOT NULL ORDER BY deletedAt DESC")
                    .stream()
                    .map(e -> new TrashItem(e.id, "debt", e.description, e.deletedAt))
                    .collect(Collectors.toList());
        } finally {
            session.enableFilter("softDelete");
        }
    }

    private DebtEntity toEntity(Debt d) {
        DebtEntity e = new DebtEntity();
        e.contact = ContactEntity.findById(d.getContactId());
        e.description = d.getDescription();
        e.totalAmount = d.getTotalAmount();
        e.status = d.getStatus() != null ? d.getStatus() : "ACTIVE";
        e.createdAt = d.getCreatedAt();
        e.user = UserEntity.findById(userContext.getUserId());
        return e;
    }

    private Debt toDomain(DebtEntity e) {
        return new Debt(
            e.id,
            e.contact != null ? e.contact.id : null,
            e.description,
            e.totalAmount,
            e.status,
            e.createdAt
        );
    }
}
