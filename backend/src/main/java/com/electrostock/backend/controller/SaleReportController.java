package com.electrostock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.electrostock.backend.dto.report.SalesByProductRow;
import com.electrostock.backend.service.SaleReportService;

@RestController
@RequestMapping("/api/reports")
public class SaleReportController {

    private final SaleReportService saleReportService;

    public SaleReportController(SaleReportService saleReportService) {
        this.saleReportService = saleReportService;
    }

    @GetMapping("/sales-by-product")
    public List<SalesByProductRow> getSalesByProduct() {
        return saleReportService.getSalesByProduct();
    }
}