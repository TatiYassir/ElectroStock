package com.electrostock.backend.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.electrostock.backend.enums.AdjustmentReason;
import com.electrostock.backend.enums.MovementType;

public record StockMovementResponse(
        Long id,
        MovementType movementType,
        int quantityChange,
        BigDecimal valueChangeMad,
        LocalDateTime movementAt,
        String serialNumber,
        AdjustmentReason adjustmentReason,
        String explanation,
        Long reversalOfMovementId) {
}