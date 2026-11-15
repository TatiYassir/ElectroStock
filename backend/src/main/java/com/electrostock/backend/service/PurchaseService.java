package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.exception.ProductNotFoundException;
import com.electrostock.backend.exception.SupplierNotFoundException;
import com.electrostock.backend.model.Currency;
import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.Purchase;
import com.electrostock.backend.model.PurchaseItem;
import com.electrostock.backend.model.Supplier;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.PurchaseRepository;
import com.electrostock.backend.repository.PurchaseItemRepository;
import com.electrostock.backend.repository.SupplierRepository;
import com.electrostock.backend.repository.CurrencyRepository;
import com.electrostock.backend.dto.PurchaseItemResponse;
import com.electrostock.backend.dto.PurchaseResponse;

@Service
public class PurchaseService {
	//Attributes :
	private final PurchaseRepository purchaseRepository;
	private final ProductRepository productRepository;
	private final PurchaseItemRepository purchaseItemRepository;
	private final SupplierRepository supplierRepository;
	private final CurrencyRepository currencyRepository;
	public PurchaseService(PurchaseRepository purchaseRepository,
	                       ProductRepository productRepository,
	                       PurchaseItemRepository purchaseItemRepository,
	                       SupplierRepository supplierRepository,
	                       CurrencyRepository currencyRepository) {
	    this.purchaseRepository = purchaseRepository;
	    this.productRepository = productRepository;
	    this.purchaseItemRepository = purchaseItemRepository;
	    this.supplierRepository = supplierRepository;
	    this.currencyRepository = currencyRepository;	    
	}
	public Purchase createDraft(Long supplierId,String currencyCode,
			LocalDate purchaseDate) {
		Supplier supplier = supplierRepository.findById(supplierId).
				orElseThrow(()-> new SupplierNotFoundException("Supplier not found."));
		Currency currency = currencyRepository.findByCode(currencyCode).
				orElseThrow(()-> new IllegalArgumentException("Currency not found."));
		Purchase purchase = new Purchase(supplier,currency,purchaseDate);
		return purchaseRepository.save(purchase);
	}
	@Transactional
	public PurchaseItem addItem(Long purchaseId, Long productId,
	                            int quantity, BigDecimal unitPriceCurrency) {
	    Purchase purchase = purchaseRepository.findById(purchaseId)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Purchase not found: " + purchaseId));

	    Product product = productRepository.findById(productId)
	            .orElseThrow(() -> new ProductNotFoundException(
	                    "Product not found: " + productId));

	    PurchaseItem item = purchase.addItem(product, quantity, unitPriceCurrency);

	    return purchaseItemRepository.save(item);
	}
	public PurchaseResponse getPurchase(Long purchaseId) {
	    Purchase purchase = purchaseRepository.findById(purchaseId)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Purchase not found: " + purchaseId));

	    List<PurchaseItem> items =
	            purchaseItemRepository.findByPurchase_Id(purchaseId);

	    List<PurchaseItemResponse> itemResponses = new ArrayList<>();

	    for (PurchaseItem item : items) {
	        PurchaseItemResponse itemResponse = new PurchaseItemResponse(
	                item.getId(),
	                item.getProduct().getId(),
	                item.getQuantity(),
	                item.getUnitPriceCurrency(),
	                item.getLineTotalCurrency());

	        itemResponses.add(itemResponse);
	    }

	    return new PurchaseResponse(
	            purchase.getId(),
	            purchase.getStatus(),
	            purchase.getCurrency().getCode(),
	            purchase.getTotalCurrency(),
	            itemResponses);
	}
}
