package com.electrostock.backend.dto.product;

import java.math.BigDecimal;

import com.electrostock.backend.enums.AdjustmentReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockAdjustmentRequest(
        @NotNull Integer quantityChange,
        @PositiveOrZero BigDecimal unitCostMad,
        @NotNull AdjustmentReason reason,
        String explanation) {
}