package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;

public record MemberRequest(
    @NotNull Long contactId
) {}
