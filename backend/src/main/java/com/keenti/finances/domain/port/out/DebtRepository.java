package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.Debt;
import java.util.List;
import java.util.Optional;

public interface DebtRepository {
    List<Debt> findAll();
    Optional<Debt> findById(Long id);
    List<Debt> findActiveByContactIdOrderByCreatedAt(Long contactId);
    Debt save(Debt debt);
    Debt update(Debt debt);
    void deleteById(Long id);
}
