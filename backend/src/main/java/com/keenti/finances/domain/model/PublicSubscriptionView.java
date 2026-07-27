package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PublicSubscriptionView(
    Long subscriptionId,
    String subscriptionName,
    BigDecimal cost,
    String billingCycle,
    LocalDate nextBillingDate,
    List<SubscriptionMemberView> members
) {
    public record SubscriptionMemberView(
        Long memberId,
        Long contactId,
        String contactName,
        BigDecimal shareAmount,
        List<PaymentRecordView> payments
    ) {}

    public record PaymentRecordView(
        Long paymentId,
        LocalDate billingDate,
        BigDecimal amount,
        String status,
        LocalDate paidDate
    ) {}
}
