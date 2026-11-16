package com.electrostock.backend.controller;

import com.electrostock.backend.dto.product.OpeningStockRequest;
import com.electrostock.backend.dto.product.StockAdjustmentRequest;
import com.electrostock.backend.service.StockService;
import com.electrostock.backend.dto.product.ProductUnitStatusResponse;

import jakarta.validation.Valid;

import java.util.List;
import com.electrostock.backend.dto.product.StockMovementResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.electrostock.backend.dto.product.TrackedStockRemovalRequest;
import com.electrostock.backend.dto.product.TrackedStockAdditionRequest;
import com.electrostock.backend.dto.product.TrackedStockRecoveryRequest;
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
    @PostMapping("/{productId}/stock-adjustments/tracked-removal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTrackedUnit(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody TrackedStockRemovalRequest request) {

        stockService.removeTrackedUnitFromStock(
                productId,
                request.serialNumber(),
                request.reason(),
                request.explanation());
    }
    @GetMapping("/{productId}/units/status")
    public ResponseEntity<ProductUnitStatusResponse> getUnitStatus(
            @PathVariable("productId") Long productId,
            @RequestParam("serialNumber") String serialNumber) {

        return ResponseEntity.of(stockService.findUnitStatus(productId, serialNumber));
    }
    @PostMapping("/{productId}/stock-adjustments/tracked-addition")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTrackedUnit(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody TrackedStockAdditionRequest request) {

        stockService.addTrackedUnitToStock(
                productId,
                request.serialNumber(),
                request.unitCostMad(),
                request.reason(),
                request.explanation());
    }
    @PostMapping("/{productId}/stock-adjustments/tracked-recovery")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recoverLostTrackedUnit(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody TrackedStockRecoveryRequest request) {

        stockService.recoverLostTrackedUnit(
                productId,
                request.serialNumber(),
                request.explanation());
    }
    @GetMapping("/{productId}/stock-movements")
    public List<StockMovementResponse> getStockMovementHistory(
            @PathVariable("productId") Long productId) {

        return stockService.getStockMovementHistory(productId);
    }
}