package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import java.util.List;

public interface FinancialAccountTransferRepository {
    List<FinancialAccountTransfer> findAll();
    FinancialAccountTransfer save(FinancialAccountTransfer transfer);
}
