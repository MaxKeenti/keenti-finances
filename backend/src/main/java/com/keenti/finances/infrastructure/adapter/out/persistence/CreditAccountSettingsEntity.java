package com.keenti.finances.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "credit_account_settings")
public class CreditAccountSettingsEntity {
    @Id
    @Column(name = "account_id")
    public Long accountId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    public FinancialAccountEntity account;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    public BigDecimal creditLimit;

    @Column(name = "statement_closing_day", nullable = false)
    public int statementClosingDay;

    @Column(name = "payment_due_day", nullable = false)
    public int paymentDueDay;
}
