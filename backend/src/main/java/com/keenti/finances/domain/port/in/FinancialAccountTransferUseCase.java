package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.FinancialAccountTransfer;
import java.util.List;

public interface FinancialAccountTransferUseCase {
    List<FinancialAccountTransfer> list();
    FinancialAccountTransfer create(FinancialAccountTransfer transfer);
}
