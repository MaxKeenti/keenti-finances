package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription")
public class SubscriptionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 200)
    public String name;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal cost;

    @Column(name = "billing_cycle", nullable = false, length = 10)
    public String billingCycle;

    @Column(nullable = false, length = 10)
    public String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    public CategoryEntity category;

    @Column(name = "next_billing_date", nullable = false)
    public LocalDate nextBillingDate;

    @Column(name = "token_uuid", length = 36, unique = true)
    public String tokenUuid;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "owner_participates", nullable = false)
    public boolean ownerParticipates = true;
}
