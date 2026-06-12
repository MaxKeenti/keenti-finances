package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.persistence.HibernateSessions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;

@ApplicationScoped
@com.keenti.finances.infrastructure.adapter.in.rest.UserScoped
public class PanacheSubscriptionRepository implements SubscriptionRepository {

    @Inject
    UserContext userContext;

    @Inject
    EntityManager em;

    @Override
    public List<Subscription> findAll() {
        return SubscriptionEntity.<SubscriptionEntity>find("ORDER BY createdAt DESC")
            .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Subscription> findById(Long id) {
        return SubscriptionEntity.<SubscriptionEntity>find("id = ?1", id)
            .firstResultOptional().map(this::toDomain);
    }

    @Override
    public Subscription save(Subscription subscription) {
        SubscriptionEntity entity = toEntity(subscription);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public Subscription update(Subscription subscription) {
        SubscriptionEntity entity = SubscriptionEntity.findById(subscription.getId());
        entity.name = subscription.getName();
        entity.cost = subscription.getCost();
        entity.billingCycle = subscription.getBillingCycle();
        entity.type = subscription.getType();
        entity.category = subscription.getCategoryId() != null
            ? CategoryEntity.findById(subscription.getCategoryId()) : null;
        entity.nextBillingDate = subscription.getNextBillingDate();
        entity.tokenUuid = subscription.getTokenUuid();
        entity.ownerParticipates = subscription.isOwnerParticipates();
        return toDomain(entity);
    }

    @Override
    public void deleteById(Long id) {
        SubscriptionEntity.deleteById(id);
    }

    @Override
    public Optional<Subscription> findByTokenUuid(String tokenUuid) {
        return SubscriptionEntity.<SubscriptionEntity>find("tokenUuid", tokenUuid)
            .firstResultOptional().map(this::toDomain);
    }

    @Override
    public void softDeleteById(Long id) {
        SubscriptionEntity.update("deletedAt = ?1 WHERE id = ?2", LocalDateTime.now(), id);
    }

    @Override
    public void restoreById(Long id) {
        Session session = HibernateSessions.unwrap(em);
        session.disableFilter("softDelete");
        try {
            SubscriptionEntity entity = SubscriptionEntity.findById(id);
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
            return SubscriptionEntity.<SubscriptionEntity>find(
                    "id = ?1 AND deletedAt IS NOT NULL", id)
                    .firstResultOptional()
                    .map(e -> new TrashItem(e.id, "subscription", e.name, e.deletedAt));
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
            return SubscriptionEntity.<SubscriptionEntity>find(
                    "deletedAt IS NOT NULL ORDER BY deletedAt DESC")
                    .stream()
                    .map(e -> new TrashItem(e.id, "subscription", e.name, e.deletedAt))
                    .toList();
        } finally {
            session.enableFilter("softDelete");
        }
    }

    private SubscriptionEntity toEntity(Subscription s) {
        SubscriptionEntity e = new SubscriptionEntity();
        e.name = s.getName();
        e.cost = s.getCost();
        e.billingCycle = s.getBillingCycle();
        e.type = s.getType();
        e.category = s.getCategoryId() != null ? CategoryEntity.findById(s.getCategoryId()) : null;
        e.nextBillingDate = s.getNextBillingDate();
        e.tokenUuid = s.getTokenUuid();
        e.createdAt = s.getCreatedAt();
        e.ownerParticipates = s.isOwnerParticipates();
        e.user = UserEntity.findById(userContext.getUserId());
        return e;
    }

    private Subscription toDomain(SubscriptionEntity e) {
        return new Subscription(
            e.id, e.name, e.cost, e.billingCycle, e.type,
            e.category != null ? e.category.id : null,
            e.nextBillingDate, e.tokenUuid, e.createdAt, e.ownerParticipates
        );
    }
}
