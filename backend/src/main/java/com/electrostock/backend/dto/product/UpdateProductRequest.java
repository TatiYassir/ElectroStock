package com.electrostock.backend.dto.product;

import java.math.BigDecimal;

public class UpdateProductRequest {
	private String sku;
	private String name;
	private String description;
	private BigDecimal sellingPriceMad;
	public String getSku() {
		return sku;
	}
	public void setSku(String sku) {
		this.sku = sku;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public BigDecimal getSellingPriceMad() {
		return sellingPriceMad;
	}
	public void setSellingPriceMad(BigDecimal sellingPriceMad) {
		this.sellingPriceMad = sellingPriceMad;
	}
	
	
}
