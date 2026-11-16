package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.Category;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.dto.product.ProductStockResponse;
import com.electrostock.backend.exception.product.ProductAlreadyExistsException;
import com.electrostock.backend.exception.product.ProductNotFoundException;
import com.electrostock.backend.repository.StockMovementRepository;
@Service
public class ProductService {
	// ATTRIBUTES :
	private final ProductRepository productRepository;
	private final CategoryService categoryService;
	private final StockMovementRepository stockMovementRepository;
	
	//CONSTRATCORS :
	public ProductService( ProductRepository productRepository,
			CategoryService categoryService,
			StockMovementRepository stockMovementRepository) {
		this.categoryService = categoryService;
		this.productRepository = productRepository;
		this.stockMovementRepository = stockMovementRepository;
	}
	// Methods :
	//create product :
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
	//get all the products :
	public List<Product> getAllProducts(){
		return productRepository.findAll();
	}
	//find product by it's ID :
	public Product getProductById(Long id) {
	    return productRepository.findById(id).
	    		orElseThrow(() -> new ProductNotFoundException("Product not found"));
	}
	//update the product :
	
	public Product updateProduct(Long Id,String sku, String name, 
			String description, BigDecimal sellingPriceMad) {
		if(sku == null || sku.isBlank()) {
			throw new IllegalArgumentException("Product SKU cannot be blank"); 
		}
		sku = sku.strip();
		Product product = getProductById(Id);
		if(productRepository.existsBySkuAndIdNot(sku, Id)) {
			throw new ProductAlreadyExistsException("SKU already exists");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Product name cannot be blank");
		}
		name = name.strip();
		
		product.setSku(sku);
		product.setName(name);
		product.setDescription(description);
		product.setSellingPriceMad(sellingPriceMad);
		return productRepository.save(product);
	}
	public ProductStockResponse getProductStock(Long productId) {
	    getProductById(productId);

	    long quantity = stockMovementRepository.getStockQuantity(productId);
	    return new ProductStockResponse(productId, quantity);
	}
}









