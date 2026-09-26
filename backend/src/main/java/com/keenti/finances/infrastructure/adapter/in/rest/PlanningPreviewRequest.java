package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PlanningPreview;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.ReceiptKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Structural D5 request. Violations here are 4xx with no preview. Money values
 * are deliberately unconstrained: a non-positive, over-limit or fractional-cent
 * amount is a business-invalid row, reported by index in the 200 envelope.
 * There is no receipt amount and no baseline total: the server reloads both.
 */
public record PlanningPreviewRequest(
    @NotNull @Min(30) @Max(30) Integer horizonDays,
    @NotNull Boolean essentialsReviewed,
    @NotNull @Size(max = 50) List<@NotNull @Valid Item> items,
    @NotNull @Size(max = 50) List<@NotNull @Valid ExpectedReceipt> expectedReceipts
) {
    public record Item(
        @NotNull BigDecimal amount,
        @NotNull LocalDate date,
        @Size(max = 200) String description,
        Long boxId,
        BigDecimal boxAmount,
        @NotNull Boolean notYetRecordedConfirmed
    ) {}

    public record ExpectedReceipt(
        @NotNull Long recordId,
        @NotNull ReceiptKind recordKind,
        @NotNull LocalDate date
    ) {}

    PlanningPreview.Request toDomain() {
        return new PlanningPreview.Request(essentialsReviewed,
            items.stream().map(item -> new PlanningPreview.CostInput(item.amount(), item.date(),
                item.description(), item.boxId(), item.boxAmount(), item.notYetRecordedConfirmed()))
                .toList(),
            expectedReceipts.stream().map(receipt -> new PlanningPreview.ReceiptSelection(
                receipt.recordKind(), receipt.recordId(), receipt.date())).toList());
    }
}
