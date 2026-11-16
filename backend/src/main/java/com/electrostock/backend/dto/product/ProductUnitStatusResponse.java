package com.electrostock.backend.dto.product;

import com.electrostock.backend.enums.UnitStatus;

public record ProductUnitStatusResponse(
        String serialNumber,
        UnitStatus status) {
}