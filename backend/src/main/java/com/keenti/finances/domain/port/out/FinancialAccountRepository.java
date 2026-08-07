package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.FinancialAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialAccountRepository {
    List<FinancialAccount> findAll(boolean archived);
    Optional<FinancialAccount> findById(Long id);
    Optional<FinancialAccount> lockById(Long id);
    FinancialAccount save(FinancialAccount account);
    FinancialAccount setHue(Long id, int hue);
    FinancialAccount setArchived(Long id, boolean archived);
    boolean existsActiveByName(String name);
    boolean isTrackingActive();
    boolean isTrackingSetupRequired();
    Optional<LocalDate> getTrackingActivationDate();
    void lockTrackingScope();
    void activateTracking(LocalDate activationDate);
    BigDecimal getTotalBalance();
    BigDecimal getBalanceAt(Long accountId, LocalDate date);
}
