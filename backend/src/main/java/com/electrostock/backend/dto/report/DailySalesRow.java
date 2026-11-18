package com.electrostock.backend.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesRow(
        LocalDate date,
        BigDecimal revenueMad,
        BigDecimal profitMad,
        long unitsSold) {}