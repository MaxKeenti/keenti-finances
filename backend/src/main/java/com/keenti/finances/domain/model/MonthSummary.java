package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public class MonthSummary {

    private final int month;
    private final BigDecimal ingress;
    private final BigDecimal egress;

    public MonthSummary(int month, BigDecimal ingress, BigDecimal egress) {
        this.month = month;
        this.ingress = ingress;
        this.egress = egress;
    }

    public int getMonth() { return month; }
    public BigDecimal getIngress() { return ingress; }
    public BigDecimal getEgress() { return egress; }
}
