package com.electrostock.backend.dto.report;

import java.time.LocalDateTime;
import com.electrostock.backend.enums.MovementType;

public record RecentStockActivityRow(
        MovementType movementType,
        String sku,
        int quantityChange,
        LocalDateTime movementAt) {
}