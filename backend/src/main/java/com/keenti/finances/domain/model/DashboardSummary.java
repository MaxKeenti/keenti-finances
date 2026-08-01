package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.util.List;

public class DashboardSummary {

    private final int year;
    private final BigDecimal netBalance;
    private final BigDecimal inBoxes;
    private final BigDecimal availableToSpend;
    private final BigDecimal totalIngress;
    private final BigDecimal totalEgress;
    private final List<MonthSummary> monthly;

    public DashboardSummary(int year, BigDecimal netBalance, BigDecimal inBoxes,
                            BigDecimal availableToSpend, BigDecimal totalIngress,
                            BigDecimal totalEgress, List<MonthSummary> monthly) {
        this.year = year;
        this.netBalance = netBalance;
        this.inBoxes = inBoxes;
        this.availableToSpend = availableToSpend;
        this.totalIngress = totalIngress;
        this.totalEgress = totalEgress;
        this.monthly = monthly;
    }

    public int getYear() { return year; }
    public BigDecimal getNetBalance() { return netBalance; }
    public BigDecimal getInBoxes() { return inBoxes; }
    public BigDecimal getAvailableToSpend() { return availableToSpend; }
    public BigDecimal getTotalIngress() { return totalIngress; }
    public BigDecimal getTotalEgress() { return totalEgress; }
    public List<MonthSummary> getMonthly() { return monthly; }
}
