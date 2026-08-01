package com.keenti.finances.infrastructure.adapter.in.rest;

public record BoxTransferResponse(
    BoxResponse sourceBox,
    BoxResponse targetBox,
    BoxBalanceSummaryResponse summary
) {}
