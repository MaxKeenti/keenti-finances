package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.PlanningPreview;

public interface PlanningPreviewUseCase {
    PlanningPreview.Result preview(PlanningPreview.Request request);
}
