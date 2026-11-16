package com.electrostock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

import com.electrostock.backend.dto.report.LowStockProductRow;

import org.springframework.data.jpa.repository.JpaRepository;
import com.electrostock.backend.model.Product;

public interface ProductRepository 
		extends JpaRepository<Product, Long>{
		boolean existsBySku(String sku);
		boolean existsByCategory_Id(Long categoryId);
		boolean existsBySkuAndIdNot(String sku, Long id);
		@Query("""
		        SELECT new com.electrostock.backend.dto.report.LowStockProductRow(
		            p.id,
		            p.sku,
		            p.name,
		            COALESCE(SUM(m.quantityChange), 0L),
		            p.lowStockThreshold
		        )
		        FROM Product p
		        LEFT JOIN StockMovement m ON m.product = p
		        WHERE p.status = com.electrostock.backend.enums.ProductStatus.ACTIVE
		        GROUP BY p.id, p.sku, p.name, p.lowStockThreshold
		        HAVING COALESCE(SUM(m.quantityChange), 0L) <= p.lowStockThreshold
		        ORDER BY COALESCE(SUM(m.quantityChange), 0L), p.sku, p.id
		        """)
		List<LowStockProductRow> findLowStockProducts();
}
