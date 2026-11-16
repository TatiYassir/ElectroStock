package com.electrostock.backend.dto;

import java.time.LocalDate;

public record CreateSaleRequest(String currencyCode, LocalDate saleDate) {
}