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
    public List<StockMovementReportRow> getStockMovementReport(
            LocalDate from, LocalDate to) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required");
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The from date cannot be after the to date");
        }

        return stockMovementRepository.findMovementReport(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
    }
}