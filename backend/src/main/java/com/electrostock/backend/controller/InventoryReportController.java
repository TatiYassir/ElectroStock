package com.electrostock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}