package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import com.keenti.finances.domain.model.TrashItem;
import java.util.List;

public interface FinancialAccountTransferUseCase {
    List<FinancialAccountTransfer> list();
    FinancialAccountTransfer create(FinancialAccountTransfer transfer);
    FinancialAccountTransfer update(Long id, FinancialAccountTransfer transfer);
    void delete(Long id);
    void restore(Long id);
    void permanentDelete(Long id);
    List<TrashItem> listDeleted();
}
