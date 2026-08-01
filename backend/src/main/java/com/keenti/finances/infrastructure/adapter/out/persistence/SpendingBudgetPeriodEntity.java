package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "spending_budget_period")
public class SpendingBudgetPeriodEntity extends PanacheEntityBase {

    @Id
    @Column(name = "period_id")
    public Long periodId;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal deposits;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal withdrawals;

    @Column(name = "transfers_in", nullable = false, precision = 12, scale = 2)
    public BigDecimal transfersIn;

    @Column(name = "transfers_out", nullable = false, precision = 12, scale = 2)
    public BigDecimal transfersOut;

    @Column(name = "funded_spending", nullable = false, precision = 12, scale = 2)
    public BigDecimal fundedSpending;

    @Column(name = "suggested_top_up", nullable = false, precision = 12, scale = 2)
    public BigDecimal suggestedTopUp;
}
