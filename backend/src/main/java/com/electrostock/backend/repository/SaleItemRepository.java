package com.electrostock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.electrostock.backend.model.SaleItem;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySale_Id(Long saleId);
}