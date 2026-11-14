package com.electrostock.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.electrostock.backend.model.Supplier;
public interface SupplierRepository 
		extends JpaRepository<Supplier, Long>{

}
