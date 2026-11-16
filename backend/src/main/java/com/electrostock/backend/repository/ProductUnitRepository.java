package com.electrostock.backend.repository;

import com.electrostock.backend.model.ProductUnit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductUnitRepository 
		extends JpaRepository<ProductUnit, Long> {
	boolean existsBySerialNumber(String serialNumber);
	List<ProductUnit> findByPurchaseItem_Id(Long purchaseItemId);
	Optional<ProductUnit> findBySerialNumber(String serialNumber);
}