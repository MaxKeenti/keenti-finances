package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * Daily reference rates quoted against one base currency: one unit of
 * {@code base} buys {@code rates.get(code)} units of {@code code}.
 *
 * <p>Display-only (ADR-0024). These rates produce the Converted Total and
 * nothing else; no ledger figure, Box, plan, or validation ever reads them.
 */
public record ExchangeRates(String base, LocalDate date, Map<String, BigDecimal> rates) {

    public ExchangeRates {
        rates = Map.copyOf(rates);
    }

    /** Converts an amount held in {@code currency} into the base currency. */
    public BigDecimal toBase(BigDecimal amount, String currency) {
        if (base.equals(currency)) {
            return amount;
        }
        BigDecimal rate = rates.get(currency);
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalStateException("No reference rate for " + currency + " against " + base);
        }
        return amount.divide(rate, MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_EVEN);
    }
}
