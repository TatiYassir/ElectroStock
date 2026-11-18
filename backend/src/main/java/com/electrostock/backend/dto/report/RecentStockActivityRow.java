package com.electrostock.backend.dto.report;

import java.time.LocalDateTime;
import com.electrostock.backend.enums.MovementType;

public record RecentStockActivityRow(
        MovementType movementType,
        String sku,
        String productName,
        int quantityChange,
        LocalDateTime movementAt) {
}