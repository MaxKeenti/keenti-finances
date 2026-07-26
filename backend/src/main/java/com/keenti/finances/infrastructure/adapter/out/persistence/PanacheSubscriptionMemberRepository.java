package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.SubscriptionMember;
import com.keenti.finances.domain.port.out.SubscriptionMemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheSubscriptionMemberRepository implements SubscriptionMemberRepository {

    @Override
    public List<SubscriptionMember> findBySubscriptionId(Long subscriptionId) {
        return SubscriptionMemberEntity.<SubscriptionMemberEntity>find("subscription.id", subscriptionId)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<SubscriptionMember> findBySubscriptionIdAndId(Long subscriptionId, Long id) {
        return SubscriptionMemberEntity.<SubscriptionMemberEntity>find(
                "subscription.id = ?1 and id = ?2", subscriptionId, id)
            .firstResultOptional()
            .map(this::toDomain);
    }

    @Override
    public SubscriptionMember save(SubscriptionMember member) {
        SubscriptionMemberEntity entity = toEntity(member);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public void deleteById(Long id) {
        SubscriptionMemberEntity.deleteById(id);
    }

    @Override
    public void updateShareAmounts(Long subscriptionId, BigDecimal shareAmount) {
        SubscriptionMemberEntity.update("shareAmount = ?1 WHERE subscription.id = ?2", shareAmount, subscriptionId);
    }

    private SubscriptionMemberEntity toEntity(SubscriptionMember m) {
        SubscriptionMemberEntity e = new SubscriptionMemberEntity();
        e.subscription = SubscriptionEntity.findById(m.getSubscriptionId());
        e.contact = ContactEntity.findById(m.getContactId());
        e.shareAmount = m.getShareAmount();
        e.createdAt = m.getCreatedAt();
        return e;
    }

    private SubscriptionMember toDomain(SubscriptionMemberEntity e) {
        return new SubscriptionMember(
            e.id,
            e.subscription != null ? e.subscription.id : null,
            e.contact != null ? e.contact.id : null,
            e.shareAmount,
            e.createdAt
        );
    }
}
