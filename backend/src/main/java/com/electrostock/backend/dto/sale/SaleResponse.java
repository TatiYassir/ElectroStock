package com.electrostock.backend.dto.sale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.electrostock.backend.enums.SaleStatus;

public record SaleResponse(
        Long id,
        SaleStatus status,
        String currencyCode,
        LocalDate saleDate,
        BigDecimal totalCurrency,
        BigDecimal totalMad,
        BigDecimal rateToMadSnapshot,
        String rateSourceSnapshot,
        LocalDateTime rateRetrievedAtSnapshot,
        boolean rateFromCacheSnapshot,
        LocalDateTime handedOverAt,
        List<SaleItemResponse> items) {
}