package com.electrostock.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.dto.report.SalesByProductRow;
import com.electrostock.backend.repository.SaleItemRepository;

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
}