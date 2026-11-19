package com.electrostock.backend.dto.sale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.electrostock.backend.enums.SaleStatus;

public record SaleSummaryResponse(
        Long id,
        SaleStatus status,
        String currencyCode,
        LocalDate saleDate,
        BigDecimal totalCurrency,
        LocalDateTime handedOverAt) {
}
