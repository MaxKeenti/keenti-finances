package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.SavingGoalDetails;
import com.keenti.finances.domain.model.SavingGoalRevisionPreview;
import com.keenti.finances.domain.model.SavingGoalTermsChange;
import java.util.List;
import java.util.Optional;

public interface SavingGoalUseCase {
    List<BoxPlan> listPlans(Long boxId);
    Optional<SavingGoalDetails> getActive(Long boxId);
    Optional<SavingGoalDetails> get(Long boxId, Long planId);
    SavingGoalDetails create(Long boxId, SavingGoalTermsChange terms);
    SavingGoalRevisionPreview previewRevision(Long boxId, Long planId,
                                              SavingGoalTermsChange changes);
    SavingGoalDetails applyRevision(Long boxId, Long planId,
                                    SavingGoalTermsChange changes);
    SavingGoalDetails confirmCompletion(Long boxId, Long planId);
    SavingGoalDetails abandon(Long boxId, Long planId);
}
