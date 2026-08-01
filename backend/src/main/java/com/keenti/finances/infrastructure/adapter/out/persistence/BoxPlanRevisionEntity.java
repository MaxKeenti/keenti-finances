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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "box_plan_revision")
public class BoxPlanRevisionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    public BoxPlanEntity plan;

    @Column(name = "effective_from", nullable = false)
    public LocalDate effectiveFrom;

    @Column(nullable = false, length = 12)
    public String cadence;

    @Column(name = "anchor_weekday")
    public Integer anchorWeekday;

    @Column(name = "anchor_day_of_month")
    public Integer anchorDayOfMonth;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "superseded_at")
    public LocalDateTime supersededAt;
}
