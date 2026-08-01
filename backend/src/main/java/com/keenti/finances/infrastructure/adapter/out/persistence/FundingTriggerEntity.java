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
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@FilterDef(name = "userScope", parameters = @ParamDef(name = "userId", type = Long.class))
@Filter(name = "userScope", condition = "user_id = :userId")
@Entity
@Table(name = "funding_trigger")
public class FundingTriggerEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "box_id", nullable = false)
    public BoxEntity box;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    public CategoryEntity category;

    @Column(nullable = false, length = 24)
    public String strategy;

    @Column(name = "fixed_amount", precision = 12, scale = 2)
    public BigDecimal fixedAmount;

    @Column(precision = 7, scale = 4)
    public BigDecimal percentage;

    @Column(nullable = false)
    public boolean enabled;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
