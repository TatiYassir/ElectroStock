package com.electrostock.backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;

import com.electrostock.backend.dto.report.SalesByProductRow;
import org.springframework.data.jpa.repository.JpaRepository;
import com.electrostock.backend.dto.report.SalesByCategoryRow;
import com.electrostock.backend.model.SaleItem;
import com.electrostock.backend.dto.report.GrossProfitByProductRow;

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
    @Query("""
            SELECT new com.electrostock.backend.dto.report.SalesByCategoryRow(
                si.categoryIdSnapshot,
                COALESCE(si.categoryNameSnapshot, 'Unknown category'),
                SUM(si.quantity),
                SUM(si.lineTotalMad)
            )
            FROM SaleItem si
            JOIN si.sale s
            WHERE s.status = com.electrostock.backend.enums.SaleStatus.CONFIRMED
            GROUP BY si.categoryIdSnapshot, si.categoryNameSnapshot
            ORDER BY SUM(si.quantity) DESC,
                     COALESCE(si.categoryNameSnapshot, 'Unknown category'),
                     si.categoryIdSnapshot
            """)
    List<SalesByCategoryRow> findSalesByCategory();
    @Query("""
            SELECT new com.electrostock.backend.dto.report.GrossProfitByProductRow(
                p.id,
                p.sku,
                p.name,
                SUM(si.lineTotalMad),
                SUM(si.costOfGoodsSoldMad),
                SUM(si.lineTotalMad - si.costOfGoodsSoldMad)
            )
            FROM SaleItem si
            JOIN si.sale s
            JOIN si.product p
            WHERE s.status = com.electrostock.backend.enums.SaleStatus.CONFIRMED
            GROUP BY p.id, p.sku, p.name
            ORDER BY SUM(si.lineTotalMad - si.costOfGoodsSoldMad) DESC,
                     p.sku,
                     p.id
            """)
    List<GrossProfitByProductRow> findGrossProfitByProduct();
}