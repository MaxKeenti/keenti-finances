package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.SubscriptionMember;
import java.util.List;
import java.util.Optional;

public interface SubscriptionMemberRepository {
    List<SubscriptionMember> findBySubscriptionId(Long subscriptionId);
    Optional<SubscriptionMember> findById(Long id);
    SubscriptionMember save(SubscriptionMember member);
    void deleteById(Long id);
    void updateShareAmounts(Long subscriptionId, java.math.BigDecimal shareAmount);
}
