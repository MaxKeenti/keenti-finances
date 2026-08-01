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
import java.time.LocalDate;

@Entity
@Table(name = "saving_goal_revision")
public class SavingGoalRevisionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "revision_id")
    public Long revisionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id")
    public BoxPlanRevisionEntity revision;

    @Column(name = "target_amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal targetAmount;

    @Column(name = "target_date", nullable = false)
    public LocalDate targetDate;

    @Column(name = "regular_commitment", nullable = false, precision = 12, scale = 2)
    public BigDecimal regularCommitment;
}
