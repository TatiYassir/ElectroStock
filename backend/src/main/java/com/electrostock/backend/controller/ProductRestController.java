package com.electrostock.backend.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.springframework.http.HttpStatus;

import com.electrostock.backend.model.Product;
import com.electrostock.backend.dto.CreateProductRequest;
import com.electrostock.backend.service.ProductService;
@RestController
@RequestMapping("/api/products")
public class ProductRestController {
	//Attributes :
	private final ProductService productservice;
	//Constructor :
	public ProductRestController(ProductService productservice) {
		this.productservice = productservice;
	}
	
	//Methods :
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Product createProduct(@RequestBody CreateProductRequest request) {
		if(request.getCategoryId() == null) {
			throw new IllegalArgumentException("Category Id cannot be null");
		}
		if(request.getTracksSerials() == null) {
			throw new IllegalArgumentException(
			        "Serial tracking must be explicitly selected");
		}
		return productservice.
				createProduct(request.getCategoryId(), request.getSku(),
						request.getName(), request.getTracksSerials(),
						request.getDescription(), request.getSellingPriceMad());
	}
	
}
