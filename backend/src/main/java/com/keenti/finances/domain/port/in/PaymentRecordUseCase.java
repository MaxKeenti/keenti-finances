package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.PaymentRecord;
import java.util.List;

public interface PaymentRecordUseCase {
    List<PaymentRecord> listBySubscription(Long subscriptionId);
    PaymentRecord recordPayment(Long subscriptionId, Long paymentId);
}
