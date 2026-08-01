package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "saving_goal_period")
public class SavingGoalPeriodEntity extends PanacheEntityBase {

    @Id
    @Column(name = "period_id")
    public Long periodId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public BoxPlanPeriodEntity period;

    @Column(name = "regular_commitment", nullable = false, precision = 12, scale = 2)
    public BigDecimal regularCommitment;

    @Column(name = "opening_arrears", nullable = false, precision = 12, scale = 2)
    public BigDecimal openingArrears;

    @Column(name = "required_amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal requiredAmount;

    @Column(name = "arrears_covered", nullable = false, precision = 12, scale = 2)
    public BigDecimal arrearsCovered;

    @Column(name = "regular_progress", nullable = false, precision = 12, scale = 2)
    public BigDecimal regularProgress;

    @Column(name = "extra_progress", nullable = false, precision = 12, scale = 2)
    public BigDecimal extraProgress;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal shortfall;

    @Column(nullable = false, length = 12)
    public String status;
}
