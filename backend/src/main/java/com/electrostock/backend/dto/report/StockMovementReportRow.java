package com.electrostock.backend.dto.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.electrostock.backend.enums.MovementType;

public record StockMovementReportRow(
        Long movementId,
        String sku,
        MovementType movementType,
        int quantityChange,
        BigDecimal valueChangeMad,
        LocalDateTime movementAt) {
}