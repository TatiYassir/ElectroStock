package com.electrostock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.electrostock.backend.service.CategoryService;
import com.electrostock.backend.model.Category;
@RestController
@RequestMapping("/api/categories")
public class CategoryRestController {
	//Attributes :
	private final CategoryService categoryService;
	//constructor-based dependency injection : 
	public CategoryRestController (CategoryService categoryService) {
		this.categoryService = categoryService;
	}
	@GetMapping 
	public List<Category> getAllCategories (){
		return categoryService.getAllCategories();
	}
	@GetMapping("/{id}")
	public Category getCategoryById(@PathVariable Long id) {
		return categoryService.getCategoryById(id);
	}
}
