package com.electrostock.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.dto.report.InventoryValuationRow;
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
}