package com.electrostock.backend.dto.purchase;

import java.math.BigDecimal;

public class PurchaseItemResponse {
	private final Long id;
	private final Long productId;
	private final int quantity;
	private final BigDecimal unitPriceCurrency;
	private final BigDecimal lineTotalCurrency;
	//Constructor :
	public PurchaseItemResponse(
			Long id,
			Long productId,
			int quantity,
			BigDecimal unitPriceCurrency,
			BigDecimal lineTotalCurrency) {
		this.id = id;
		this.productId = productId;
		this.quantity = quantity;
		this.unitPriceCurrency = unitPriceCurrency;
		this.lineTotalCurrency = lineTotalCurrency;
	}
	public Long getId() {
		return id;
	}
	public Long getProductId() {
		return productId;
	}
	public int getQuantity() {
		return quantity;
	}
	public BigDecimal getUnitPriceCurrency() {
		return unitPriceCurrency;
	}
	public BigDecimal getLineTotalCurrency() {
		return lineTotalCurrency;
	}
	
}
