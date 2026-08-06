package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import com.keenti.finances.domain.model.TrashItem;
import java.util.List;
import java.util.Optional;

public interface FinancialAccountTransferRepository {
    List<FinancialAccountTransfer> findAll();
    Optional<FinancialAccountTransfer> findById(Long id);
    FinancialAccountTransfer save(FinancialAccountTransfer transfer);
    FinancialAccountTransfer update(FinancialAccountTransfer transfer);
    void softDeleteById(Long id);
    void restoreById(Long id);
    void deleteById(Long id);
    Optional<TrashItem> findDeletedById(Long id);
    List<TrashItem> findAllDeleted();
}
