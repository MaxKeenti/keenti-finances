package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BoxReorderRequest(
    @NotNull List<@Valid @NotNull Long> boxIds
) {}
