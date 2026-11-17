package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeRateQuote(
        BigDecimal rateToMad,
        String source,
        LocalDateTime retrievedAt,
        boolean fromCache) {
}