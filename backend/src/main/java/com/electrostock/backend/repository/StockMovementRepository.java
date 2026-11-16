package com.electrostock.backend.repository;

import com.electrostock.backend.model.StockMovement;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository 
		extends JpaRepository<StockMovement, Long> {
	List<StockMovement> findByPurchaseItem_Id(Long purchaseItemId);
}