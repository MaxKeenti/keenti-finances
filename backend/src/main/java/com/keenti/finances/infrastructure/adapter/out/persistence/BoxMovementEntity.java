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
@Table(name = "box_movement")
public class BoxMovementEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "movement_type", nullable = false, length = 16)
    public String movementType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_box_id")
    public BoxEntity sourceBox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_box_id")
    public BoxEntity destinationBox;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(name = "effective_date", nullable = false)
    public LocalDate effectiveDate;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transaction_id")
    public TransactionEntity sourceTransaction;

    @Column(name = "source_transaction_reference")
    public Long sourceTransactionReference;

    @Column(name = "source_transaction_order")
    public Integer sourceTransactionOrder;

    @Column(name = "source_transaction_changed", nullable = false)
    public boolean sourceTransactionChanged;
}
