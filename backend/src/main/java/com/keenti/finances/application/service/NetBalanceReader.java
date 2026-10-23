package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.BoxBalanceSummary;
import com.keenti.finances.domain.model.Currencies;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.PrimaryCurrencyProvider;
import com.keenti.finances.domain.port.out.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one place the headline formulas are evaluated, always in one currency
 * (ADR-0020, ADR-0022, ADR-0024).
 *
 * <pre>
 * Net Balance(c)        = tracking active ? signed Account balances in c
 *                                         : INGRESS - EGRESS Transactions in c
 * In Boxes(c)           = active Box balances in c
 * Available to Spend(c) = Net Balance(c) - In Boxes(c)
 * </pre>
 */
@ApplicationScoped
public class NetBalanceReader {

    @Inject
    FinancialAccountRepository financialAccountRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    BoxRepository boxRepository;

    @Inject
    PrimaryCurrencyProvider primaryCurrencyProvider;

    public BigDecimal netBalance(String currency) {
        return financialAccountRepository.isTrackingActive()
            ? financialAccountRepository.getTotalBalance(currency)
            : transactionRepository.getNetBalance(currency);
    }

    public BoxBalanceSummary summary(String currency) {
        BigDecimal netBalance = netBalance(currency);
        BigDecimal inBoxes = boxRepository.getTotalBalance(currency);
        return new BoxBalanceSummary(currency, netBalance, inBoxes, netBalance.subtract(inBoxes));
    }

    /**
     * The Viewing Currency for a summary request: the one asked for, or the
     * Primary Currency when none was.
     */
    public String viewingCurrency(String requested) {
        String normalized = Currencies.normalize(requested);
        if (normalized == null) {
            return primaryCurrencyProvider.primaryCurrency();
        }
        if (!Currencies.isSupported(normalized)) {
            throw new BadRequestException("Unsupported currency: " + requested);
        }
        return normalized;
    }

    /**
     * Every currency the User holds money in, Primary Currency first, then the
     * rest in the supported order so the switcher does not reshuffle.
     */
    public List<String> currenciesInUse() {
        Set<String> used = new LinkedHashSet<>();
        used.add(primaryCurrencyProvider.primaryCurrency());
        Set<String> held = new LinkedHashSet<>(financialAccountRepository.findActiveCurrencies());
        held.addAll(boxRepository.findActiveCurrencies());
        for (String code : Currencies.SUPPORTED) {
            if (held.contains(code)) {
                used.add(code);
            }
        }
        return new ArrayList<>(used);
    }
}
