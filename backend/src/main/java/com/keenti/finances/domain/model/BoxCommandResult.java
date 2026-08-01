package com.keenti.finances.domain.model;

public record BoxCommandResult(
    Box box,
    BoxBalanceSummary summary
) {}
