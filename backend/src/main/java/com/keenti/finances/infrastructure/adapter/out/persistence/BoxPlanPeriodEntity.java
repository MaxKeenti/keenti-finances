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
@Table(name = "box_plan_period")
public class BoxPlanPeriodEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    public BoxPlanEntity plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    public BoxPlanRevisionEntity revision;

    @Column(name = "period_start", nullable = false)
    public LocalDate periodStart;

    @Column(name = "period_end_exclusive", nullable = false)
    public LocalDate periodEndExclusive;

    @Column(name = "opening_balance", nullable = false, precision = 12, scale = 2)
    public BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false, precision = 12, scale = 2)
    public BigDecimal closingBalance;

    @Column(name = "net_progress", nullable = false, precision = 12, scale = 2)
    public BigDecimal netProgress;

    @Column(name = "evaluated_at", nullable = false)
    public LocalDateTime evaluatedAt;
}
