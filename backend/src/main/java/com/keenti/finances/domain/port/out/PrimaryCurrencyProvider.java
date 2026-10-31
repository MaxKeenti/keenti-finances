package com.keenti.finances.domain.port.out;

/** The current User's Primary Currency (ADR-0024). */
public interface PrimaryCurrencyProvider {
    String primaryCurrency();
}
