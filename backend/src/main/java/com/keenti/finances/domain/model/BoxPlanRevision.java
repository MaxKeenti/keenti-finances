package com.keenti.finances.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BoxPlanRevision(
    Long id,
    Long planId,
    LocalDate effectiveFrom,
    PlanCadence cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    LocalDateTime createdAt,
    LocalDateTime supersededAt
) {}
