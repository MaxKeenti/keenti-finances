package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.AccountTrackingStatus;
import com.keenti.finances.domain.model.FinancialAccount;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialAccountUseCase {
    AccountTrackingStatus status();
    List<FinancialAccount> list(boolean archived);
    Optional<FinancialAccount> getById(Long id);
    List<FinancialAccount> activate(LocalDate activationDate, List<FinancialAccount> accounts);
    FinancialAccount create(FinancialAccount account);
    FinancialAccount updateHue(Long id, int hue);
    FinancialAccount archive(Long id);
    FinancialAccount restore(Long id);
}
