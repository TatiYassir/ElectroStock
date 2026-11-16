package com.electrostock.backend.dto.product;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OpeningStockRequest(
        @NotNull @Positive Integer quantity,
        @NotNull @PositiveOrZero BigDecimal unitCostMad,
        List<String> serialNumbers) {
}