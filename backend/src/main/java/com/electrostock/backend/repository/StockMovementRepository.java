package com.electrostock.backend.repository;

import com.electrostock.backend.enums.MovementType;
import com.electrostock.backend.model.StockMovement;
import com.electrostock.backend.dto.report.InventoryValuationRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


import com.electrostock.backend.dto.report.StockMovementReportRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository 
		extends JpaRepository<StockMovement, Long> {
	List<StockMovement> findByPurchaseItem_Id(Long purchaseItemId);
	@Query("""
		    SELECT COALESCE(SUM(m.quantityChange), 0)
		    FROM StockMovement m
		    WHERE m.product.id = :productId
		    """)
		Long getStockQuantity(@Param("productId") Long productId);
	@Query("""
		    SELECT COALESCE(SUM(m.valueChangeMad), 0)
		    FROM StockMovement m
		    WHERE m.product.id = :productId
		    """)
		BigDecimal getStockValueMad(@Param("productId") Long productId);
	List<StockMovement> findBySaleItem_Sale_IdAndMovementType(
	        Long saleId, MovementType movementType);
	List<StockMovement> findByPurchaseItem_Purchase_IdAndMovementType(
	        Long purchaseId, MovementType movementType);
	List<StockMovement> findByProduct_IdOrderByMovementAtDescIdDesc(Long productId);
	@Query("""
		    SELECT new com.electrostock.backend.dto.report.InventoryValuationRow(
		        p.id,
		        p.sku,
		        p.name,
		        COALESCE(SUM(m.quantityChange), 0L),
		        COALESCE(SUM(m.valueChangeMad), 0.0BD)
		    )
		    FROM Product p
		    LEFT JOIN StockMovement m ON m.product = p
		    GROUP BY p.id, p.sku, p.name
		    ORDER BY p.sku, p.id
		    """)
		List<InventoryValuationRow> findInventoryValuation();
	@Query("""
	        SELECT m
	        FROM StockMovement m
	        WHERE m.movementAt >= :fromInclusive
	          AND m.movementAt < :toExclusive
	        ORDER BY m.movementAt DESC, m.id DESC
	        """)
	List<StockMovement> findMovementsInPeriod(
	        @Param("fromInclusive") LocalDateTime fromInclusive,
	        @Param("toExclusive") LocalDateTime toExclusive);
	@Query("""
		    SELECT new com.electrostock.backend.dto.report.StockMovementReportRow(
		        m.id,
		        p.id,
		        p.sku,
		        p.name,
		        m.movementType,
		        m.quantityChange,
		        m.valueChangeMad,
		        m.movementAt,
		        u.serialNumber,
		        m.adjustmentReason,
		        m.explanation,
		        original.id
		    )
		    FROM StockMovement m
		    JOIN m.product p
		    LEFT JOIN m.productUnit u
		    LEFT JOIN m.reversalOfMovement original
		    WHERE m.movementAt >= :fromInclusive
		      AND m.movementAt < :toExclusive
		    ORDER BY m.movementAt DESC, m.id DESC
		    """)
		List<StockMovementReportRow> findMovementReport(
		        @Param("fromInclusive") LocalDateTime fromInclusive,
		        @Param("toExclusive") LocalDateTime toExclusive);
}