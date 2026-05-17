package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheSubscriptionRepository implements SubscriptionRepository {

    @Override
    public List<Subscription> findAll() {
        return SubscriptionEntity.<SubscriptionEntity>find("ORDER BY createdAt DESC")
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Subscription> findById(Long id) {
        return SubscriptionEntity.<SubscriptionEntity>findByIdOptional(id).map(this::toDomain);
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
    public List<Subscription> findWithNextBillingDateBefore(LocalDate cutoff) {
        return SubscriptionEntity.<SubscriptionEntity>find("nextBillingDate <= ?1", cutoff)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Subscription> findByTokenUuid(String tokenUuid) {
        return SubscriptionEntity.<SubscriptionEntity>find("tokenUuid", tokenUuid)
            .firstResultOptional().map(this::toDomain);
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
