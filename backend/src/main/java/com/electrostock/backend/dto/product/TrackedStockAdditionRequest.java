package com.electrostock.backend.dto.product;

import java.math.BigDecimal;

import com.electrostock.backend.enums.AdjustmentReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TrackedStockAdditionRequest(
        @NotBlank @Size(max = 150) String serialNumber,
        @NotNull @PositiveOrZero BigDecimal unitCostMad,
        @NotNull AdjustmentReason reason,
        String explanation) {
}