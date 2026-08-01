package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.BoxPlanPeriod;
import com.keenti.finances.domain.model.BoxPlanRevision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BoxPlanRepository {
    List<BoxPlan> findAllByBoxId(Long boxId);
    Optional<BoxPlan> findById(Long boxId, Long planId);
    Optional<BoxPlan> findActiveByBoxId(Long boxId);
    Optional<BoxPlan> lockActiveByBoxId(Long boxId);
    Optional<BoxPlan> lockById(Long boxId, Long planId);
    BoxPlan save(BoxPlan plan);
    BoxPlan updateStatus(Long planId, BoxPlan.Status status,
                         LocalDateTime closedAt, BigDecimal completionAmount);
    void touch(Long planId, LocalDateTime updatedAt);

    List<BoxPlanRevision> findRevisions(Long planId, boolean includeSuperseded);
    Optional<BoxPlanRevision> findRevisionById(Long planId, Long revisionId);
    BoxPlanRevision saveRevision(BoxPlanRevision revision);
    void supersedeUnopenedRevisions(Long planId, LocalDate effectiveFrom,
                                    LocalDateTime supersededAt);

    List<BoxPlanPeriod> findPeriods(Long planId);
    BoxPlanPeriod savePeriod(BoxPlanPeriod period);
    BoxPlanPeriod updatePeriod(BoxPlanPeriod period);
}
