package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.SubscriptionMember;
import java.util.List;

public interface SubscriptionMemberRepository {
    List<SubscriptionMember> findBySubscriptionId(Long subscriptionId);
    SubscriptionMember save(SubscriptionMember member);
    boolean deleteBySubscriptionIdAndId(Long subscriptionId, Long id);
    void updateShareAmounts(Long subscriptionId, java.math.BigDecimal shareAmount);
}
