package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_msi_plan")
public class CreditMsiPlanEntity extends PanacheEntityBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id") public FinancialAccountEntity account;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id") public TransactionEntity transaction;
    @Column(name = "purchase_amount") public BigDecimal purchaseAmount;
    @Column(name = "installment_count") public int installmentCount;
    @Column(name = "first_installment_date") public LocalDate firstInstallmentDate;
    @Column(name = "opening_balance_amount") public BigDecimal openingBalanceAmount;
    @Column(name = "cancelled_at") public LocalDateTime cancelledAt;
    @Column(name = "ended_at") public LocalDateTime endedAt;
    @Column(name = "end_reason") public String endReason;
}
