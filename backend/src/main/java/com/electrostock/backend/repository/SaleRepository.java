package com.electrostock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.electrostock.backend.dto.sale.SaleSummaryResponse;
import com.electrostock.backend.model.Sale;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Query("""
            SELECT new com.electrostock.backend.dto.sale.SaleSummaryResponse(
                s.id, s.status, c.code, s.saleDate, s.totalCurrency, s.handedOverAt
            )
            FROM Sale s JOIN s.currency c
            ORDER BY s.saleDate DESC, s.id DESC
            """)
    List<SaleSummaryResponse> findSaleSummaries();
}
