package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.enums.ProductStatus;
import com.electrostock.backend.exception.product.ProductNotFoundException;
import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.ProductUnit;
import com.electrostock.backend.model.StockMovement;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.ProductUnitRepository;
import com.electrostock.backend.repository.StockMovementRepository;

@Service
public class StockService {

    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CostingService costingService;

    public StockService(ProductRepository productRepository,
                        ProductUnitRepository productUnitRepository,
                        StockMovementRepository stockMovementRepository,
                        CostingService costingService) {
        this.productRepository = productRepository;
        this.productUnitRepository = productUnitRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.costingService = costingService;
    }

    @Transactional
    public void recordOpeningStock(
            Long productId, int quantity, BigDecimal unitCostMad,
            List<String> serialNumbers) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found: " + productId));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new IllegalStateException("Opening stock requires an active product");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Opening quantity must be positive");
        }

        if (unitCostMad == null || unitCostMad.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit cost is required and cannot be negative");
        }

        List<String> serials = validateOpeningSerials(
                product, quantity, serialNumbers);
        LocalDateTime movementAt = LocalDateTime.now();

        if (product.isTracksSerials()) {
            for (String serial : serials) {
                ProductUnit unit = productUnitRepository.save(
                        new ProductUnit(product, null, serial, unitCostMad));

                stockMovementRepository.save(StockMovement.forOpeningStock(
                        product, unit, 1, unitCostMad, movementAt));
            }
        } else {
            BigDecimal totalValueMad =
                    unitCostMad.multiply(BigDecimal.valueOf(quantity));

            stockMovementRepository.saveAndFlush(StockMovement.forOpeningStock(
                    product, null, quantity, totalValueMad, movementAt));

            product.updateAverageCostMad(
                    costingService.calculateAverageCostMad(productId));
        }
    }

    private List<String> validateOpeningSerials(
            Product product, int quantity, List<String> serialNumbers) {

        List<String> supplied = serialNumbers == null ? List.of() : serialNumbers;
        int expected = product.isTracksSerials() ? quantity : 0;

        if (supplied.size() != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " serial numbers for "
                            + product.getSku() + ", but got " + supplied.size());
        }

        List<String> cleanedSerials = new ArrayList<>();
        Set<String> seenSerials = new HashSet<>();

        for (String serial : supplied) {
            if (serial == null || serial.isBlank()) {
                throw new IllegalArgumentException("Serial number is required");
            }

            String cleaned = serial.strip();

            if (cleaned.length() > 150) {
                throw new IllegalArgumentException(
                        "Serial number must be at most 150 characters");
            }

            if (!seenSerials.add(cleaned)) {
                throw new IllegalArgumentException("Duplicate serial number: " + cleaned);
            }

            if (productUnitRepository.existsBySerialNumber(cleaned)) {
                throw new IllegalArgumentException(
                        "Serial number is already registered: " + cleaned);
            }

            cleanedSerials.add(cleaned);
        }

        return cleanedSerials;
    }
}