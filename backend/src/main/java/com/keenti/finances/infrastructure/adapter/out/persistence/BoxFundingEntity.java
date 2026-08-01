package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@FilterDef(name = "userScope", parameters = @ParamDef(name = "userId", type = Long.class))
@Filter(name = "userScope", condition = "user_id = :userId")
@Entity
@Table(name = "box_funding")
public class BoxFundingEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "transaction_id", nullable = false)
    public Long transactionId;

    @Column(name = "box_id", nullable = false)
    public Long boxId;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(name = "line_order", nullable = false)
    public int lineOrder;

    @Column(name = "effective_date", nullable = false)
    public LocalDate effectiveDate;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
