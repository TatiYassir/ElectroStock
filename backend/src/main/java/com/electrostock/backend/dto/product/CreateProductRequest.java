package com.electrostock.backend.dto.product;

import java.math.BigDecimal;

public class CreateProductRequest {
	//Attributes :
	private Long categoryId;
	private String sku;
	private String name;
	private Boolean tracksSerials;
	private String description ; 
	private BigDecimal sellingPriceMad;
	private int warrantyMonths;
	private int lowStockThreshold;
	//Getters :
	public Long getCategoryId() {
		return categoryId;
	}
	public String getSku() {
		return sku;
	}
	public String getName() {
		return name;
	}
	public Boolean getTracksSerials() {
		return tracksSerials;
	}
	public String getDescription() {
		return description;
	}
	public BigDecimal getSellingPriceMad() {
		return sellingPriceMad;
	}
	public int getWarrantyMonths() {
	    return warrantyMonths;
	}

	public int getLowStockThreshold() {
	    return lowStockThreshold;
	}
	//Setters :
	
	public void setSku(String sku) {
		this.sku = sku;
	}
	public void setCategoryId(Long categoryId) {
		this.categoryId = categoryId;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setTracksSerials(Boolean tracksSerials) {
		this.tracksSerials = tracksSerials;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public void setSellingPriceMad(BigDecimal sellingPriceMad) {
		this.sellingPriceMad = sellingPriceMad;
	}
	public void setWarrantyMonths(int warrantyMonths) {
	    this.warrantyMonths = warrantyMonths;
	}

	public void setLowStockThreshold(int lowStockThreshold) {
	    this.lowStockThreshold = lowStockThreshold;
	}
}
