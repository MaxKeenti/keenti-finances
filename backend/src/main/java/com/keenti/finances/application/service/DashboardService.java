package com.keenti.finances.application.service;

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

    @Override
    public DashboardSummary getSummary(int year) {
        LOG.infof("dashboard.aggregation year=%d", year);

        List<MonthSummary> monthly = transactionRepository.findMonthlySummary(year);
        BigDecimal netBalance = transactionRepository.getNetBalance();

        BigDecimal totalIngress = monthly.stream()
                .map(MonthSummary::getIngress)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgress = monthly.stream()
                .map(MonthSummary::getEgress)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LOG.infof("dashboard.aggregation year=%d months=%d totalIngress=%s totalEgress=%s netBalance=%s",
                year, monthly.size(), totalIngress, totalEgress, netBalance);

        return new DashboardSummary(year, netBalance, totalIngress, totalEgress, monthly);
    }
}
