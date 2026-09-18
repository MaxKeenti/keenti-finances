package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real generation loop at one instant on opposite calendar days. */
class BillingCalendarTest {
    private static final Instant INSTANT = Instant.parse("2026-09-08T04:30:00Z");

    @Test
    void sameInstantGeneratesOnlyPeriodsDueInTheCallersZone() {
        for (String zone : List.of("America/Mexico_City", "Asia/Tokyo")) {
            for (int day : List.of(6, 7, 8, 9)) {
                Harness harness = new Harness(zone, LocalDate.of(2026, 9, day));
                boolean due = day <= (zone.equals("Asia/Tokyo") ? 8 : 7);
                assertEquals(OptionalInt.of(due ? 1 : 0), harness.service.generateForSubscription(1L),
                    zone + " due date " + day);
                assertEquals(due ? 1 : 0, harness.payments.size());
                assertEquals(LocalDate.of(2026, 9, day).plusMonths(due ? 1 : 0),
                    harness.subscription.getNextBillingDate());
                assertEquals(OptionalInt.of(0), harness.service.generateForSubscription(1L),
                    "repeating a manual action must not duplicate or pre-generate periods");
            }
        }
    }

    @Test
    void catchUpRemainsBoundedAndCanContinueOnAnotherClick() {
        Harness harness = new Harness("America/Mexico_City", LocalDate.of(1900, 1, 1));
        assertEquals(OptionalInt.of(600), harness.service.generateForSubscription(1L));
        assertEquals(LocalDate.of(1950, 1, 1), harness.subscription.getNextBillingDate());
        assertEquals(OptionalInt.of(600), harness.service.generateForSubscription(1L));
        assertEquals(1200, harness.payments.size());
    }

    private static final class Harness {
        final BillingService service = new BillingService();
        final List<PaymentRecord> payments = new ArrayList<>();
        Subscription subscription;

        Harness(String zone, LocalDate due) {
            subscription = new Subscription(1L, "Synthetic subscription", BigDecimal.TEN,
                "MONTHLY", "PERSONAL", null, due, "synthetic-token",
                LocalDateTime.of(2026, 1, 1, 0, 0), true);
            service.userTimeZoneProvider = new UserTimeZoneProvider() {
                public ZoneId getTimeZone() { return ZoneId.of(zone); }
                public LocalDate today() { return LocalDate.now(Clock.fixed(INSTANT, getTimeZone())); }
            };
            // Only operations used by this generation loop are allowed. An unexpected
            // repository operation fails the test rather than returning a silent default.
            service.subscriptionRepository = (SubscriptionRepository) Proxy.newProxyInstance(
                SubscriptionRepository.class.getClassLoader(), new Class<?>[]{SubscriptionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(subscription);
                    case "update" -> subscription = (Subscription) args[0];
                    default -> throw new AssertionError("Unexpected subscription operation: " + method.getName());
                });
            service.paymentRecordRepository = (PaymentRecordRepository) Proxy.newProxyInstance(
                PaymentRecordRepository.class.getClassLoader(), new Class<?>[]{PaymentRecordRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsBySubscriptionIdAndBillingDateAndMemberId" -> payments.stream()
                        .anyMatch(payment -> payment.getBillingDate().equals(args[1]));
                    case "save" -> { PaymentRecord payment = (PaymentRecord) args[0]; payments.add(payment); yield payment; }
                    default -> throw new AssertionError("Unexpected payment operation: " + method.getName());
                });
        }
    }
}
