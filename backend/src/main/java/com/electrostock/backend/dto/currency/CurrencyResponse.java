package com.electrostock.backend.dto.currency;

public record CurrencyResponse(
        String code,
        String name,
        String symbol) {
}