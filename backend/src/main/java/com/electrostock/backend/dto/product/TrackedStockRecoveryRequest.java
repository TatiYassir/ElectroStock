package com.electrostock.backend.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackedStockRecoveryRequest(
        @NotBlank @Size(max = 150) String serialNumber,
        String explanation) {
}