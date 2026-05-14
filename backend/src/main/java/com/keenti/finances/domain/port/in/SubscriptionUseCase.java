package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.SubscriptionMember;
import java.util.List;
import java.util.Optional;

public interface SubscriptionUseCase {
    List<Subscription> list();
    Optional<Subscription> getById(Long id);
    Subscription create(Subscription subscription);
    Subscription update(Long id, Subscription subscription);
    void delete(Long id);
    SubscriptionMember addMember(Long subscriptionId, Long contactId);
    void removeMember(Long subscriptionId, Long memberId);
    List<SubscriptionMember> listMembers(Long subscriptionId);
    Optional<Subscription> getByToken(String tokenUuid);
}
