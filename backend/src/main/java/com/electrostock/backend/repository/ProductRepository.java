package com.electrostock.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.electrostock.backend.model.Product;

public interface ProductRepository 
		extends JpaRepository<Product, Long>{
		boolean existsBySku(String sku);
		boolean existsByCategory_Id(Long categoryId);
		boolean existsBySkuAndIdNot(String sku, Long id);
}
