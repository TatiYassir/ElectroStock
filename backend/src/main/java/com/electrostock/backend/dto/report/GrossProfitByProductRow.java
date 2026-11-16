package com.electrostock.backend.dto.report;

import java.math.BigDecimal;

public record GrossProfitByProductRow(
        Long productId,
        String sku,
        String productName,
        BigDecimal revenueMad,
        BigDecimal costMad,
        BigDecimal grossProfitMad) {
}