package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.BoxBalanceSummary;
import com.keenti.finances.domain.model.DashboardSummary;
import com.keenti.finances.domain.model.MonthSummary;
import com.keenti.finances.domain.port.in.DashboardUseCase;
import com.keenti.finances.domain.port.out.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DashboardService implements DashboardUseCase {

    private static final Logger LOG = Logger.getLogger(DashboardService.class);

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    NetBalanceReader netBalanceReader;

    @Override
    public DashboardSummary getSummary(int year, String requestedCurrency) {
        String currency = netBalanceReader.viewingCurrency(requestedCurrency);
        LOG.infof("dashboard.aggregation year=%d currency=%s", year, currency);

        List<MonthSummary> monthly = transactionRepository.findMonthlySummary(year, currency);
        BoxBalanceSummary headline = netBalanceReader.summary(currency);

        BigDecimal totalIngress = monthly.stream()
                .map(MonthSummary::getIngress)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgress = monthly.stream()
                .map(MonthSummary::getEgress)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LOG.infof("dashboard.aggregation year=%d currency=%s months=%d totalIngress=%s totalEgress=%s netBalance=%s inBoxes=%s availableToSpend=%s",
                year, currency, monthly.size(), totalIngress, totalEgress, headline.netBalance(),
                headline.inBoxes(), headline.availableToSpend());

        return new DashboardSummary(year, currency, headline.netBalance(), headline.inBoxes(),
                headline.availableToSpend(), totalIngress, totalEgress, monthly);
    }
}
