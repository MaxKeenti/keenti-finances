package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.DashboardSummary;

public interface DashboardUseCase {
    DashboardSummary getSummary(int year);
}
