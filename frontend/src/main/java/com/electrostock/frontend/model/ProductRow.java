package com.electrostock.frontend.model;

import java.math.BigDecimal;

public record ProductRow(
        Long id,
        String sku,
        String name,
        BigDecimal sellingPriceMad) {
}