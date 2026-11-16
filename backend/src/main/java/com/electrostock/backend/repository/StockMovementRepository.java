package com.electrostock.backend.repository;

import com.electrostock.backend.enums.MovementType;
import com.electrostock.backend.model.StockMovement;

import java.math.BigDecimal;
import java.util.List;
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
}