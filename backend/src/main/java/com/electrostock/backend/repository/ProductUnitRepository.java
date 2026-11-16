package com.electrostock.backend.repository;

import com.electrostock.backend.model.ProductUnit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, Long> {
}