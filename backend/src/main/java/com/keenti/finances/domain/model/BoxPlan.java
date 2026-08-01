package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BoxPlan(
    Long id,
    Long boxId,
    Type type,
    Status status,
    LocalDate startDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt,
    BigDecimal completionAmount
) {
    public enum Type {
        SAVING_GOAL,
        SPENDING_BUDGET
    }

    public enum Status {
        ACTIVE,
        READY_TO_COMPLETE,
        OVERDUE,
        COMPLETED,
        ABANDONED,
        ENDED;

        public boolean isActive() {
            return this == ACTIVE || this == READY_TO_COMPLETE || this == OVERDUE;
        }
    }
}
