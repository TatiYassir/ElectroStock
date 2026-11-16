package com.electrostock.backend.dto.report;

import java.math.BigDecimal;

public record SalesByCategoryRow(
        Long categoryId,
        String categoryName,
        Long unitsSold,
        BigDecimal revenueMad) {
}