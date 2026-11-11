package com.electrostock.backend.service;


import java.util.List;

import org.springframework.stereotype.Service;
import com.electrostock.backend.repository.CategoryRepository;
import com.electrostock.backend.model.Category;
import com.electrostock.backend.exception.CategoryNotFoundException;
import com.electrostock.backend.exception.CategoryAlreadyExistsException;

@Service
public class CategoryService {
	// ATTRIBUTES :
	private final CategoryRepository categoryRepository;
	//constructor-based dependency injection : 
	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}
	// methods :
	// create a category :
	public Category createCategory(String name , String description) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException ("Category name cannot be blank");
		}
		name = name.strip();
		if (name.length() > 100) {
			throw new IllegalArgumentException ("Category name cannot exceed 100 characters");
		}
		if ( categoryRepository.existsByName(name)) {
			throw new CategoryAlreadyExistsException ("Category already exists");
		}
		Category category = new Category(name, description);

		return categoryRepository.save(category);
	}
	// return a list of all categories :
	public List<Category> getAllCategories (){
		return categoryRepository.findAll();
	}
	// find a category by it's Id :
	public Category getCategoryById(Long id) {
		return categoryRepository.findById(id).
				orElseThrow(() -> new CategoryNotFoundException("Category not found"));
	}
	//update the name of the category
	public Category updateCategory(String newName, Long id, String description) {
		if (newName == null|| id == null || newName.isBlank()) {
			throw new IllegalArgumentException ("Category name and id cannot be blank");
		}
		newName = newName.strip();
		if (newName.length() > 100) {
			throw new IllegalArgumentException ("Category name cannot exceed 100 characters");
		}
		Category category = getCategoryById(id);
		if (categoryRepository.existsByNameAndIdNot(newName, id)) {
		    throw new CategoryAlreadyExistsException("Category already exists");
		}
		category.setName(newName);
		category.setDescription(description);
		return categoryRepository.save(category);
	}
}
