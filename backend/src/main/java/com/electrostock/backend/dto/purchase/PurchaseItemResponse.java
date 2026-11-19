package com.electrostock.backend.dto.purchase;

import java.math.BigDecimal;
import java.util.List;

public class PurchaseItemResponse {
	private final Long id;
	private final Long productId;
	private final int quantity;
	private final BigDecimal unitPriceCurrency;
	private final BigDecimal lineTotalCurrency;
	private final String sku;
	private final String productName;
	private final boolean tracksSerials;
	private final List<String> serialNumbers;
	//Constructor :
	public PurchaseItemResponse(
			Long id,
			Long productId,
			int quantity,
			BigDecimal unitPriceCurrency,
			BigDecimal lineTotalCurrency,
			String sku, String productName, boolean tracksSerials, List<String> serialNumbers) {
		this.id = id;
		this.productId = productId;
		this.quantity = quantity;
		this.unitPriceCurrency = unitPriceCurrency;
		this.lineTotalCurrency = lineTotalCurrency;
		this.sku = sku;
		this.productName = productName;
		this.tracksSerials = tracksSerials;
		this.serialNumbers = List.copyOf(serialNumbers);
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
	public String getSku() { return sku; }
	public String getProductName() { return productName; }
	public boolean isTracksSerials() { return tracksSerials; }
	public List<String> getSerialNumbers() { return serialNumbers; }
	
}
