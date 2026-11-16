package com.electrostock.backend.dto.purchase;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class AddPurchaseItemRequest {
	@NotNull
	@Positive
	private Long productId;

	@NotNull
	@Positive
	private Integer quantity;

	@NotNull
	@PositiveOrZero
	private BigDecimal unitPriceCurrency;
	public Long getProductId() {
		return productId;
	}
	public void setProductId(Long productId) {
		this.productId = productId;
	}
	public Integer getQuantity() {
	    return quantity;
	}

	public void setQuantity(Integer quantity) {
	    this.quantity = quantity;
	}
	public BigDecimal getUnitPriceCurrency() {
		return unitPriceCurrency;
	}
	public void setUnitPriceCurrency(BigDecimal unitPriceCurrency) {
		this.unitPriceCurrency = unitPriceCurrency;
	}
	
}
