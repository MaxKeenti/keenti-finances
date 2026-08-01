package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.FundingTrigger;
import java.util.List;
import java.util.Optional;

public interface FundingTriggerRepository {
    List<FundingTrigger> findAllByBoxId(Long boxId);
    Optional<FundingTrigger> findById(Long boxId, Long triggerId);
    List<FundingTrigger> findEnabledByCategoryId(Long categoryId);
    boolean existsByBoxAndCategory(Long boxId, Long categoryId, Long excludingId);
    FundingTrigger save(FundingTrigger trigger);
    FundingTrigger update(FundingTrigger trigger);
    void delete(Long boxId, Long triggerId);
}
