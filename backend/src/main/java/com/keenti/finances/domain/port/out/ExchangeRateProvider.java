package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.ExchangeRates;

/**
 * The latest daily reference rates for the Converted Total (ADR-0024).
 *
 * <p>Implementations throw when rates cannot be read. Callers must report the
 * Converted Total as unavailable then, never as zero.
 */
public interface ExchangeRateProvider {
    ExchangeRates latest(String base);
}
