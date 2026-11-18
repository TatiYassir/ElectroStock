package com.electrostock.backend.dto.report;

import java.math.BigDecimal;

public record InventoryValuationRow(
        Long productId,
        String sku,
        String productName,
        long stockQuantity,
        BigDecimal stockValueMad,
        BigDecimal sellingPriceMad) {
}