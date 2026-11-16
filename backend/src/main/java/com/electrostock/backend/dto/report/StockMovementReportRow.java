package com.electrostock.backend.dto.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.electrostock.backend.enums.AdjustmentReason;
import com.electrostock.backend.enums.MovementType;

public record StockMovementReportRow(
        Long id,
        Long productId,
        String sku,
        String productName,
        MovementType movementType,
        int quantityChange,
        BigDecimal valueChangeMad,
        LocalDateTime movementAt,
        String serialNumber,
        AdjustmentReason adjustmentReason,
        String explanation,
        Long reversalOfMovementId) {
}