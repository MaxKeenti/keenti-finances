package com.keenti.finances.infrastructure.adapter.out.persistence;

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

@Entity
@Table(name = "credit_statement_payment")
public class CreditStatementPaymentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "statement_id", nullable = false) public CreditStatementEntity statement;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transfer_id", nullable = false) public FinancialAccountTransferEntity transfer;
    @Column(nullable = false, precision = 12, scale = 2) public BigDecimal amount;
    @Column(name = "created_at", nullable = false) public LocalDateTime createdAt;
}
