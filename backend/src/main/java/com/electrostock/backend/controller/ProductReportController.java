package com.electrostock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.electrostock.backend.dto.report.LowStockProductRow;
import com.electrostock.backend.service.ProductReportService;

@RestController
@RequestMapping("/api/reports")
public class ProductReportController {

    private final ProductReportService productReportService;

    public ProductReportController(ProductReportService productReportService) {
        this.productReportService = productReportService;
    }

    @GetMapping("/low-stock")
    public List<LowStockProductRow> getLowStockProducts() {
        return productReportService.getLowStockProducts();
    }
}