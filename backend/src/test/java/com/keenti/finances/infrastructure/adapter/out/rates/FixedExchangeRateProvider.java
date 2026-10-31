package com.keenti.finances.infrastructure.adapter.out.rates;

import com.keenti.finances.domain.model.ExchangeRates;
import com.keenti.finances.domain.port.out.ExchangeRateProvider;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Deterministic reference rates for tests: 1 EUR = 20 MXN, 1 USD = 18 MXN,
 * 1 GBP = 25 MXN. Tests never reach the network.
 */
@Mock
@ApplicationScoped
public class FixedExchangeRateProvider implements ExchangeRateProvider {

    public static final LocalDate RATES_DATE = LocalDate.of(2026, 9, 25);

    private static final Map<String, BigDecimal> MXN_PER_UNIT = Map.of(
        "MXN", BigDecimal.ONE,
        "EUR", new BigDecimal("20"),
        "USD", new BigDecimal("18"),
        "GBP", new BigDecimal("25"));

    @Override
    public ExchangeRates latest(String base) {
        BigDecimal basePerMxn = BigDecimal.ONE.divide(MXN_PER_UNIT.get(base), 10, java.math.RoundingMode.HALF_EVEN);
        Map<String, BigDecimal> rates = new java.util.HashMap<>();
        MXN_PER_UNIT.forEach((code, mxn) -> {
            if (!code.equals(base)) {
                // one unit of base buys (base in MXN) / (code in MXN) units of code
                rates.put(code, MXN_PER_UNIT.get(base).divide(mxn, 10, java.math.RoundingMode.HALF_EVEN));
            }
        });
        return new ExchangeRates(base, RATES_DATE, rates);
    }
}
