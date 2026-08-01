package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxHistoryEntry;
import com.keenti.finances.domain.model.BoxMovement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BoxRepository {
    List<Box> findAll(boolean archived);
    Optional<Box> findActiveById(Long id);
    Optional<Box> findByIdIncludingArchived(Long id);
    Optional<Box> lockActiveById(Long id);
    Optional<Box> lockByIdIncludingArchived(Long id);
    Box save(Box box);
    Box update(Box box);
    List<Box> reorder(List<Long> boxIds);
    Box setArchived(Long id, boolean archived, int displayOrder);
    boolean existsActiveByName(String name, Long excludingId);
    int nextDisplayOrder();
    void lockAllocationScope();
    BoxMovement saveMovement(BoxMovement movement);
    Optional<BoxMovement> lockMovementByIdForBox(Long movementId, Long boxId);
    BoxMovement updateMovement(BoxMovement movement);
    boolean replacementRemainsNonNegative(BoxMovement movement);
    List<BoxHistoryEntry> findHistory(Long boxId);
    BigDecimal getBalance(Long boxId);
    BigDecimal getTotalBalance();
    boolean canDebit(Long boxId, BigDecimal amount, LocalDate effectiveDate,
                     LocalDateTime createdAt);
}
