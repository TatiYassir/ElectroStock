package com.electrostock.backend.service;

import org.springframework.stereotype.Service;

import com.electrostock.backend.repository.StockMovementRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CostingService {

    private final StockMovementRepository stockMovementRepository;

    public CostingService(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }
    public BigDecimal calculateAverageCostMad(Long productId) {
        long quantity = stockMovementRepository.getStockQuantity(productId);
        BigDecimal value = stockMovementRepository.getStockValueMad(productId);

        if (quantity <= 0) {
            throw new IllegalStateException(
                    "Average cost requires positive stock quantity");
        }

        return value.divide(
                BigDecimal.valueOf(quantity),
                6,
                RoundingMode.HALF_UP);
    }
}