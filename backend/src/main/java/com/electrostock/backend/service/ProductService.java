package com.electrostock.backend.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.electrostock.backend.model.Product;
import com.electrostock.backend.exception.ProductAlreadyExistsException;
import com.electrostock.backend.model.Category;
import com.electrostock.backend.repository.ProductRepository;
@Service
public class ProductService {
	// ATTRIBUTES :
	private final ProductRepository productRepository;
	private final CategoryService categoryService;
	
	//CONSTRATCORS :
	public ProductService( ProductRepository productRepository,
			CategoryService categoryService) {
		this.categoryService = categoryService;
		this.productRepository = productRepository;
	}
	// Methods :
	public Product createProduct(
	        Long categoryId,
	        String sku,
	        String name,
	        boolean tracksSerials,
	        String description,
	        BigDecimal sellingPriceMad) {

	    // Your implementation
		Category category = categoryService.getCategoryById(categoryId);
		Product product = new Product(
			    category, sku, name, tracksSerials,
			    description, sellingPriceMad
			);
		if (productRepository.existsBySku(product.getSku())) {
		    throw new ProductAlreadyExistsException(
		        "Product SKU already exists");
		}
		return productRepository.save(product);
	}
}
