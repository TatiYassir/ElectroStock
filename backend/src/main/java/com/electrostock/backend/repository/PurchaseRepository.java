package com.electrostock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

import com.electrostock.backend.dto.report.PurchasesBySupplierRow;
import org.springframework.data.jpa.repository.JpaRepository;
import com.electrostock.backend.model.Purchase;

public interface PurchaseRepository 
		extends JpaRepository<Purchase , Long>{
	@Query("""
	        SELECT new com.electrostock.backend.dto.report.PurchasesBySupplierRow(
	            s.id, s.name, COUNT(p.id), SUM(p.totalMad)
	        )
	        FROM Purchase p
	        JOIN p.supplier s
	        WHERE p.status = com.electrostock.backend.enums.PurchaseStatus.CONFIRMED
	        GROUP BY s.id, s.name
	        ORDER BY s.name, s.id
	        """)
	List<PurchasesBySupplierRow> findPurchasesBySupplier();
}
