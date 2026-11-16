package com.electrostock.backend.dto.purchase;

import java.math.BigDecimal;
import java.util.List;

import com.electrostock.backend.enums.PurchaseStatus;

public class PurchaseResponse {
	private final Long id;
	private final PurchaseStatus status;
	private final String currencyCode;
	private final BigDecimal totalCurrency;
	private final List<PurchaseItemResponse> items;
	
	public PurchaseResponse(
			Long id,
			PurchaseStatus status,
			String currencyCode,
			BigDecimal totalCurrency,
			List<PurchaseItemResponse> items) {
		this.id = id;
		this.status = status;
		this.currencyCode = currencyCode;
		this.totalCurrency = totalCurrency;
		this.items = items;
	}

	public Long getId() {
		return id;
	}

	public PurchaseStatus getStatus() {
		return status;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public BigDecimal getTotalCurrency() {
		return totalCurrency;
	}

	public List<PurchaseItemResponse> getItems() {
		return items;
	}
	
}
