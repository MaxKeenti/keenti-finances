package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Subscription Member's still-pending contribution.
 *
 * <p>A read-side projection, not an entity: it exists so a caller that only
 * needs the pending rows does not have to load every Payment Record a
 * Subscription has ever had — including the {@code PAID} ones — to find them.
 *
 * <p>{@code contactName} is {@code null} when the Contact behind the Member has
 * been moved to the trash. The money is still owed, so the row survives; only
 * the name it would be filed under is withheld.
 */
public record PendingContribution(
    Long paymentRecordId,
    Long subscriptionId,
    String subscriptionName,
    Long memberId,
    Long contactId,
    String contactName,
    BigDecimal amount,
    LocalDate billingDate
) {}
