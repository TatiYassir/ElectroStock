package com.electrostock.backend.dto.sale;

import java.math.BigDecimal;
import java.util.List;

public record SaleItemResponse(
        Long id,
        Long productId,
        int quantity,
        BigDecimal unitPriceCurrency,
        BigDecimal lineTotalCurrency,
        String sku,
        String productName,
        boolean tracksSerials,
        List<String> serialNumbers) {
}
