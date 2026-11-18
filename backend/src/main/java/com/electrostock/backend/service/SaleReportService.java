package com.electrostock.backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.dto.report.SalesByProductRow;
import com.electrostock.backend.repository.SaleItemRepository;
import com.electrostock.backend.dto.report.SalesByCategoryRow;
import com.electrostock.backend.dto.report.DailySalesRow;
import com.electrostock.backend.dto.report.GrossProfitByProductRow;

@Service
public class SaleReportService {

    private final SaleItemRepository saleItemRepository;

    public SaleReportService(SaleItemRepository saleItemRepository) {
        this.saleItemRepository = saleItemRepository;
    }

    @Transactional(readOnly = true)
    public List<SalesByProductRow> getSalesByProduct() {
        return saleItemRepository.findSalesByProduct();
    }
    @Transactional(readOnly = true)
    public List<SalesByCategoryRow> getSalesByCategory() {
        return saleItemRepository.findSalesByCategory();
    }
    @Transactional(readOnly = true)
    public List<GrossProfitByProductRow> getGrossProfitByProduct() {
        return saleItemRepository.findGrossProfitByProduct();
    }
    @Transactional(readOnly = true)
    public List<DailySalesRow> getDailySales(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        return saleItemRepository.findDailySales(from, to);
    }
}