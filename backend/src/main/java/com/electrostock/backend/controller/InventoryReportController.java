package com.electrostock.backend.controller;

import java.util.List;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;

import com.electrostock.backend.dto.report.StockMovementReportRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.electrostock.backend.dto.report.RecentStockActivityRow;
import com.electrostock.backend.dto.report.InventoryValuationRow;
import com.electrostock.backend.service.InventoryReportService;

@RestController
@RequestMapping("/api/reports")
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;

    public InventoryReportController(InventoryReportService inventoryReportService) {
        this.inventoryReportService = inventoryReportService;
    }

    @GetMapping("/inventory-valuation")
    public List<InventoryValuationRow> getInventoryValuation() {
        return inventoryReportService.getInventoryValuation();
    }
    @GetMapping("/stock-movements")
    public List<StockMovementReportRow> getStockMovements(
            @RequestParam("from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return inventoryReportService.getStockMovementReport(from, to);
    }
    @GetMapping("/stock-movements/recent")
    public List<RecentStockActivityRow> getRecentStockActivity() {
        return inventoryReportService.getRecentStockActivity();
    }
}