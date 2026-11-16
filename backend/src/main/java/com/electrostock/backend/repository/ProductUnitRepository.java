package com.electrostock.backend.repository;

import com.electrostock.backend.model.ProductUnit;
import java.util.List;

import org.springframework.data.jpa.repository.Query;

import com.electrostock.backend.dto.report.SerialUnitTraceRow;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductUnitRepository 
		extends JpaRepository<ProductUnit, Long> {
	boolean existsBySerialNumber(String serialNumber);
	List<ProductUnit> findByPurchaseItem_Id(Long purchaseItemId);
	Optional<ProductUnit> findBySerialNumber(String serialNumber);
	@Query("""
	        SELECT new com.electrostock.backend.dto.report.SerialUnitTraceRow(
	            u.id,
	            p.sku,
	            p.name,
	            u.serialNumber,
	            u.status,
	            u.acquisitionCostMad,
	            si.id
	        )
	        FROM ProductUnit u
	        JOIN u.product p
	        LEFT JOIN SaleItemUnit siu ON siu.productUnit = u
	        LEFT JOIN siu.saleItem si
	        ORDER BY p.sku, u.serialNumber
	        """)
	List<SerialUnitTraceRow> findSerialUnitTrace();
}