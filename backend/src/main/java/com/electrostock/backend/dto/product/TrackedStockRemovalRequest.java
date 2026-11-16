package com.electrostock.backend.dto.product;

import com.electrostock.backend.enums.AdjustmentReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TrackedStockRemovalRequest(
        @NotBlank @Size(max = 150) String serialNumber,
        @NotNull AdjustmentReason reason,
        String explanation) {
}