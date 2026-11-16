package com.electrostock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.electrostock.backend.dto.report.SalesByProductRow;
import com.electrostock.backend.service.SaleReportService;
import com.electrostock.backend.dto.report.SalesByCategoryRow;
import com.electrostock.backend.dto.report.GrossProfitByProductRow;

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
    @GetMapping("/sales-by-category")
    public List<SalesByCategoryRow> getSalesByCategory() {
        return saleReportService.getSalesByCategory();
    }
    @GetMapping("/gross-profit-by-product")
    public List<GrossProfitByProductRow> getGrossProfitByProduct() {
        return saleReportService.getGrossProfitByProduct();
    }
}