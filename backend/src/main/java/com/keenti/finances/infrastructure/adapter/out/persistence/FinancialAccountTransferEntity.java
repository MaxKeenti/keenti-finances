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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@FilterDef(name = "userScope", parameters = @ParamDef(name = "userId", type = Long.class))
@Filter(name = "userScope", condition = "user_id = :userId")
@Entity
@Table(name = "financial_account_transfer")
public class FinancialAccountTransferEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    public FinancialAccountEntity sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id", nullable = false)
    public FinancialAccountEntity destinationAccount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(name = "transfer_date", nullable = false)
    public LocalDate transferDate;

    @Column(length = 500)
    public String notes;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
