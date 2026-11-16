package com.electrostock.backend.dto.sale;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

public record ConfirmSaleRequest(
        @NotNull Map<Long, List<String>> serialsByItemId) {
}