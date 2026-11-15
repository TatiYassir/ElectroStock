package com.electrostock.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.electrostock.backend.model.Purchase;

public interface PurchaseRepository 
		extends JpaRepository<Purchase , Long>{
	
}
