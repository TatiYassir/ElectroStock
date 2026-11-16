package com.electrostock.backend.controller;

import com.electrostock.backend.dto.product.OpeningStockRequest;
import com.electrostock.backend.dto.product.StockAdjustmentRequest;
import com.electrostock.backend.service.StockService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/{productId}/opening-stock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordOpeningStock(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody OpeningStockRequest request) {

        stockService.recordOpeningStock(
                productId,
                request.quantity(),
                request.unitCostMad(),
                request.serialNumbers());
    }
    @PostMapping("/{productId}/stock-adjustments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjustStock(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody StockAdjustmentRequest request) {

        stockService.adjustUntrackedStock(
                productId,
                request.quantityChange(),
                request.unitCostMad(),
                request.reason(),
                request.explanation());
    }
}