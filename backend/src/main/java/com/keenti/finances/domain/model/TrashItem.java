package com.keenti.finances.domain.model;

import java.time.LocalDateTime;

public record TrashItem(Long id, String entityType, String label, LocalDateTime deletedAt) {}
