package com.electrostock.backend.dto.sale;

import java.time.LocalDate;

public record CreateSaleRequest(String currencyCode, LocalDate saleDate) {
}