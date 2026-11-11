package com.electrostock.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.electrostock.backend.model.Category;

public interface CategoryRepository 
		extends JpaRepository<Category, Long> {
	boolean existsByName(String name);
	boolean existsByNameAndIdNot(String name, Long id);
}
