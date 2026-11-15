package com.electrostock.backend.repository;

import com.electrostock.backend.model.PurchaseItem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseItemRepository 
		extends JpaRepository<PurchaseItem, Long> {
	List<PurchaseItem> findByPurchase_Id(Long purchaseId);
}