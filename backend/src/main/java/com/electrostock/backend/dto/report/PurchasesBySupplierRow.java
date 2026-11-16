package com.electrostock.backend.dto.report;

import java.math.BigDecimal;

public record PurchasesBySupplierRow(
        Long supplierId,
        String supplierName,
        Long confirmedPurchases,
        BigDecimal totalPurchasedMad) {
}