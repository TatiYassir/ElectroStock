package com.electrostock.backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;

import com.electrostock.backend.dto.report.SalesByProductRow;
import org.springframework.data.jpa.repository.JpaRepository;

import com.electrostock.backend.model.SaleItem;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySale_Id(Long saleId);
    @Query("""
            SELECT new com.electrostock.backend.dto.report.SalesByProductRow(
                p.id, p.sku, p.name, SUM(si.quantity), SUM(si.lineTotalMad)
            )
            FROM SaleItem si
            JOIN si.sale s
            JOIN si.product p
            WHERE s.status = com.electrostock.backend.enums.SaleStatus.CONFIRMED
            GROUP BY p.id, p.sku, p.name
            ORDER BY SUM(si.quantity) DESC, p.sku, p.id
            """)
    List<SalesByProductRow> findSalesByProduct();
}