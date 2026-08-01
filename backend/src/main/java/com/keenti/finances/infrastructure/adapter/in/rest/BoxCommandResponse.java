package com.keenti.finances.infrastructure.adapter.in.rest;

public record BoxCommandResponse(
    BoxResponse box,
    BoxBalanceSummaryResponse summary
) {}
