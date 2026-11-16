package com.electrostock.backend.dto.report;

public record LowStockProductRow(
        Long productId,
        String sku,
        String productName,
        Long stockQuantity,
        int lowStockThreshold) {
}