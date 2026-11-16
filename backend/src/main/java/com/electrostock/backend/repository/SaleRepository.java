package com.electrostock.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.electrostock.backend.model.Sale;

public interface SaleRepository extends JpaRepository<Sale, Long> {
}