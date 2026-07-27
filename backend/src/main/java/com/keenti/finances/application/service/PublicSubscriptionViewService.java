package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.PublicSubscriptionView;
import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.SubscriptionMember;
import com.keenti.finances.domain.port.in.PublicSubscriptionViewUseCase;
import com.keenti.finances.domain.port.out.ContactRepository;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionMemberRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PublicSubscriptionViewService implements PublicSubscriptionViewUseCase {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    SubscriptionMemberRepository subscriptionMemberRepository;

    @Inject
    PaymentRecordRepository paymentRecordRepository;

    @Inject
    ContactRepository contactRepository;

    @Override
    @Transactional
    public Optional<PublicSubscriptionView> getByToken(String token) {
        return subscriptionRepository.findByTokenUuid(token)
            .map(this::toPublicView);
    }

    private PublicSubscriptionView toPublicView(Subscription subscription) {
        List<SubscriptionMember> members =
            subscriptionMemberRepository.findBySubscriptionId(subscription.getId());
        Map<Long, List<PaymentRecord>> paymentsByMember =
            paymentRecordRepository.findBySubscriptionId(subscription.getId()).stream()
                .filter(payment -> payment.getMemberId() != null)
                .collect(Collectors.groupingBy(PaymentRecord::getMemberId));

        List<PublicSubscriptionView.SubscriptionMemberView> memberViews = members.stream()
            .map(member -> toMemberView(member, paymentsByMember))
            .toList();

        return new PublicSubscriptionView(
            subscription.getName(),
            subscription.getCost(),
            subscription.getBillingCycle(),
            subscription.getNextBillingDate(),
            memberViews
        );
    }

    private PublicSubscriptionView.SubscriptionMemberView toMemberView(
            SubscriptionMember member,
            Map<Long, List<PaymentRecord>> paymentsByMember) {
        String contactName = contactRepository.findById(member.getContactId())
            .map(contact -> contact.getName())
            .orElse(null);
        List<PublicSubscriptionView.PaymentRecordView> payments =
            paymentsByMember.getOrDefault(member.getId(), List.of()).stream()
                .map(this::toPaymentRecordView)
                .toList();

        return new PublicSubscriptionView.SubscriptionMemberView(
            member.getId(),
            member.getContactId(),
            contactName,
            member.getShareAmount(),
            payments
        );
    }

    private PublicSubscriptionView.PaymentRecordView toPaymentRecordView(PaymentRecord payment) {
        return new PublicSubscriptionView.PaymentRecordView(
            payment.getId(),
            payment.getBillingDate(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getPaidDate()
        );
    }
}
