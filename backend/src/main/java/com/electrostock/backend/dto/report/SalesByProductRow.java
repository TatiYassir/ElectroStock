package com.electrostock.backend.dto.report;

import java.math.BigDecimal;

public record SalesByProductRow(
        Long productId,
        String sku,
        String productName,
        Long unitsSold,
        BigDecimal revenueMad) {
}