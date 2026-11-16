package com.electrostock.backend.dto.sale;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long id,
        Long productId,
        int quantity,
        BigDecimal unitPriceCurrency,
        BigDecimal lineTotalCurrency) {
}