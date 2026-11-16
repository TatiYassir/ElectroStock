package com.electrostock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.electrostock.backend.dto.report.PurchasesBySupplierRow;
import com.electrostock.backend.service.PurchaseReportService;

@RestController
@RequestMapping("/api/reports")
public class PurchaseReportController {

    private final PurchaseReportService purchaseReportService;

    public PurchaseReportController(PurchaseReportService purchaseReportService) {
        this.purchaseReportService = purchaseReportService;
    }

    @GetMapping("/purchases-by-supplier")
    public List<PurchasesBySupplierRow> getPurchasesBySupplier() {
        return purchaseReportService.getPurchasesBySupplier();
    }
}