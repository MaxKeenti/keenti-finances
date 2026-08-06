package com.keenti.finances.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_statement")
public class CreditStatementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id", nullable = false) public FinancialAccountEntity account;
    @Column(name = "period_start", nullable = false) public LocalDate periodStart;
    @Column(name = "period_end", nullable = false) public LocalDate periodEnd;
    @Column(name = "due_date", nullable = false) public LocalDate dueDate;
    @Column(name = "estimated_balance", nullable = false) public BigDecimal estimatedBalance;
    @Column(name = "official_balance") public BigDecimal officialBalance;
    @Column(name = "official_minimum_payment") public BigDecimal officialMinimumPayment;
    @Column(name = "official_avoid_interest") public BigDecimal officialAvoidInterest;
    @Column(name = "official_note") public String officialNote;
    @Column(name = "confirmed_at") public LocalDateTime confirmedAt;
}
