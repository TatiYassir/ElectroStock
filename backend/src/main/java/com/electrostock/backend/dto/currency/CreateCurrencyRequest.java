package com.electrostock.backend.dto.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCurrencyRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 10) String symbol) {
}