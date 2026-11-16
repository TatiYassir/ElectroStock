package com.electrostock.backend.dto;

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
        LocalDateTime handedOverAt,
        List<SaleItemResponse> items) {
}