package com.electrostock.backend.dto.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.electrostock.backend.enums.PurchaseStatus;

public record PurchaseSummaryResponse(
        Long id, PurchaseStatus status, String supplierName, String currencyCode,
        LocalDate purchaseDate, BigDecimal totalCurrency) {
}
