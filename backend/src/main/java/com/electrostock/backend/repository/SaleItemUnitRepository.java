package com.electrostock.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.electrostock.backend.model.SaleItemUnit;

public interface SaleItemUnitRepository extends JpaRepository<SaleItemUnit, Long> {
}