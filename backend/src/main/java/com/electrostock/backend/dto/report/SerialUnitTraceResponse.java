package com.electrostock.backend.dto.report;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import com.electrostock.backend.dto.product.StockMovementResponse;
import com.electrostock.backend.enums.UnitStatus;

public record SerialUnitTraceResponse(
        Long unitId,
        Long productId,
        String sku,
        String productName,
        String serialNumber,
        UnitStatus status,
        BigDecimal acquisitionCostMad,
        LocalDate warrantyStart,
        LocalDate warrantyEnd,
        List<StockMovementResponse> movements) {
}