package com.electrostock.backend.dto;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long id,
        Long productId,
        int quantity,
        BigDecimal unitPriceCurrency,
        BigDecimal lineTotalCurrency) {
}