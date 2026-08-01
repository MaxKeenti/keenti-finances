package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxBalanceSummary;
import com.keenti.finances.domain.model.BoxCommandResult;
import com.keenti.finances.domain.model.BoxHistoryEntry;
import com.keenti.finances.domain.model.BoxTransferResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BoxUseCase {
    List<Box> list(boolean archived);
    Optional<Box> getById(Long id);
    List<BoxHistoryEntry> history(Long id);
    BoxBalanceSummary summary();
    Box create(Box box);
    Box update(Long id, Box box);
    List<Box> reorder(List<Long> boxIds);
    BoxCommandResult deposit(Long id, BigDecimal amount, LocalDate effectiveDate);
    BoxCommandResult withdraw(Long id, BigDecimal amount, LocalDate effectiveDate);
    BoxTransferResult transfer(Long sourceBoxId, Long targetBoxId,
                               BigDecimal amount, LocalDate effectiveDate);
    BoxCommandResult correctMovement(Long boxId, Long movementId,
                                     BigDecimal amount, LocalDate effectiveDate);
    Box archive(Long id);
    Box restore(Long id);
}
