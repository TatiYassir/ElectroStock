package com.electrostock.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.dto.report.PurchasesBySupplierRow;
import com.electrostock.backend.repository.PurchaseRepository;

@Service
public class PurchaseReportService {

    private final PurchaseRepository purchaseRepository;

    public PurchaseReportService(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    @Transactional(readOnly = true)
    public List<PurchasesBySupplierRow> getPurchasesBySupplier() {
        return purchaseRepository.findPurchasesBySupplier();
    }
}