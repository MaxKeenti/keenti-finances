package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.FundingSuggestionSet;
import com.keenti.finances.domain.model.FundingTrigger;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FundingTriggerUseCase {
    List<FundingTrigger> list(Long boxId);
    Optional<FundingTrigger> getById(Long boxId, Long triggerId);
    FundingTrigger create(Long boxId, FundingTrigger trigger);
    FundingTrigger update(Long boxId, Long triggerId, FundingTrigger trigger);
    FundingTrigger setEnabled(Long boxId, Long triggerId, boolean enabled);
    void delete(Long boxId, Long triggerId);
    FundingSuggestionSet suggestions(Long categoryId, BigDecimal ingressAmount);
}
