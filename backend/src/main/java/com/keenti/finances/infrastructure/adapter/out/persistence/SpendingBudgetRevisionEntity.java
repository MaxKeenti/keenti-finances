package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "spending_budget_revision")
public class SpendingBudgetRevisionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "revision_id")
    public Long revisionId;

    @Column(name = "desired_balance", nullable = false, precision = 12, scale = 2)
    public BigDecimal desiredBalance;
}
