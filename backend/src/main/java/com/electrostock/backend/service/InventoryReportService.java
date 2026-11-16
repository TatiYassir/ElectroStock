package com.electrostock.backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.dto.report.InventoryValuationRow;
import com.electrostock.backend.dto.report.StockMovementReportRow;
import com.electrostock.backend.repository.StockMovementRepository;

@Service
public class InventoryReportService {

    private final StockMovementRepository stockMovementRepository;

    public InventoryReportService(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryValuationRow> getInventoryValuation() {
        return stockMovementRepository.findInventoryValuation();
    }

    @Transactional(readOnly = true)
    public List<StockMovementReportRow> getStockMovements(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Provide a start date and an end date on or after it");
        }

        return stockMovementRepository.findMovementsInPeriod(
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay())
                .stream()
                .map(movement -> new StockMovementReportRow(
                        movement.getId(),
                        movement.getProduct().getSku(),
                        movement.getMovementType(),
                        movement.getQuantityChange(),
                        movement.getValueChangeMad(),
                        movement.getMovementAt()))
                .toList();
    }
}