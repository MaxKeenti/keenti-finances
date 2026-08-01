package com.keenti.finances.domain.model;

public record BoxTransferResult(
    Box sourceBox,
    Box targetBox,
    BoxBalanceSummary summary
) {}
