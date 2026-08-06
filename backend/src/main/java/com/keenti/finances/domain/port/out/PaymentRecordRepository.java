package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.PaymentRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRecordRepository {
    List<PaymentRecord> findBySubscriptionId(Long subscriptionId);
    List<PaymentRecord> findBySubscriptionIdAndBillingDateForUpdate(
        Long subscriptionId, LocalDate billingDate);
    Optional<PaymentRecord> findById(Long id);
    PaymentRecord save(PaymentRecord record);
    PaymentRecord update(PaymentRecord record);
    boolean existsBySubscriptionIdAndBillingDateAndMemberId(Long subscriptionId, LocalDate billingDate, Long memberId);
    void deleteByIds(List<Long> ids);
}
