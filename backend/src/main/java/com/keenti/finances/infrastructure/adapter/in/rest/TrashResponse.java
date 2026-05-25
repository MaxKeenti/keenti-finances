package com.keenti.finances.infrastructure.adapter.in.rest;

import java.time.LocalDateTime;

public record TrashResponse(Long id, String entityType, String label, LocalDateTime deletedAt) {}
