package com.electrostock.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.dto.report.LowStockProductRow;
import com.electrostock.backend.repository.ProductRepository;

@Service
public class ProductReportService {

    private final ProductRepository productRepository;

    public ProductReportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<LowStockProductRow> getLowStockProducts() {
    	return productRepository.findStockAvailability(true);
    }
    @Transactional(readOnly = true)
    public List<LowStockProductRow> getStockAvailability() {
        return productRepository.findStockAvailability(false);
    }
}