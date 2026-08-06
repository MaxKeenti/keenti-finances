package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.CreditAccountSettings;
import java.util.Optional;

public interface CreditAccountSettingsRepository {
    Optional<CreditAccountSettings> findByAccountId(Long accountId);
    CreditAccountSettings save(CreditAccountSettings settings);
}
