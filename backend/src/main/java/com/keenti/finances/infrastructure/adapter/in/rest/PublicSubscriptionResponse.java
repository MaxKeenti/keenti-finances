package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PublicSubscriptionResponse(
    String subscriptionName,
    BigDecimal cost,
    String billingCycle,
    LocalDate nextBillingDate,
    List<MemberPaymentSummary> members
) {
    public record MemberPaymentSummary(
        Long memberId,
        Long contactId,
        String contactName,
        BigDecimal shareAmount,
        List<PaymentSummary> payments
    ) {}

    public record PaymentSummary(
        Long paymentId,
        LocalDate billingDate,
        BigDecimal amount,
        String status,
        LocalDate paidDate
    ) {}
}
