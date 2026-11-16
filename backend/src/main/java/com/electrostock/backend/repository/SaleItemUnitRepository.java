package com.electrostock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.electrostock.backend.model.SaleItemUnit;

public interface SaleItemUnitRepository 
		extends JpaRepository<SaleItemUnit, Long> {
	List<SaleItemUnit> findBySaleItem_Sale_Id(Long saleId);

}