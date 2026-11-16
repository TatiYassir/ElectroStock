package com.electrostock.backend.dto.report;

import java.math.BigDecimal;

import com.electrostock.backend.enums.UnitStatus;

public record SerialUnitTraceRow(
        Long unitId,
        String sku,
        String productName,
        String serialNumber,
        UnitStatus status,
        BigDecimal acquisitionCostMad,
        Long saleItemId) {
}