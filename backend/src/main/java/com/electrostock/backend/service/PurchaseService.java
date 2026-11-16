package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.exception.ProductNotFoundException;
import com.electrostock.backend.exception.SupplierNotFoundException;
import com.electrostock.backend.model.Currency;
import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.Purchase;
import com.electrostock.backend.model.PurchaseItem;
import com.electrostock.backend.model.Supplier;
import com.electrostock.backend.enums.PurchaseStatus;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.ProductUnitRepository;
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
	private final ProductUnitRepository productUnitRepository;
	//Constructor :
	public PurchaseService(PurchaseRepository purchaseRepository,
	                       ProductRepository productRepository,
	                       PurchaseItemRepository purchaseItemRepository,
	                       SupplierRepository supplierRepository,
	                       CurrencyRepository currencyRepository,
	                       ProductUnitRepository productUnitRepository) {
	    this.purchaseRepository = purchaseRepository;
	    this.productRepository = productRepository;
	    this.purchaseItemRepository = purchaseItemRepository;
	    this.supplierRepository = supplierRepository;
	    this.currencyRepository = currencyRepository;
	    this.productUnitRepository = productUnitRepository;
	}
	//Methods:
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
	@Transactional(readOnly = true)
	public void validatePurchaseConfirmation(
	        Long purchaseId, Map<Long, List<String>> serialsByItemId) {

	    Purchase purchase = purchaseRepository.findById(purchaseId)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Purchase not found: " + purchaseId));

	    List<PurchaseItem> items =
	            purchaseItemRepository.findByPurchase_Id(purchaseId);

	    validateConfirmation(purchase, items);
	    validatePurchaseSerials(items, serialsByItemId);
	}
	private void validateConfirmation(Purchase purchase, List<PurchaseItem> items) {
	    if (purchase.getStatus() != PurchaseStatus.DRAFT) {
	        throw new IllegalStateException("Only draft purchases can be confirmed");
	    }

	    if (items.isEmpty()) {
	        throw new IllegalStateException("Add at least one item before confirming");
	    }
	}
	private void validateSerialCount(PurchaseItem item, List<String> serialNumbers) {
	    int expectedCount = 0;

	    if (item.getProduct().isTracksSerials()) {
	        expectedCount = item.getQuantity();
	    }

	    int actualCount = serialNumbers.size();

	    if (actualCount != expectedCount) {
	        throw new IllegalArgumentException(
	                "Expected " + expectedCount + " serial numbers for "
	                        + item.getProduct().getSku() + ", but got " + actualCount);
	    }
	}
	private void validateSerialValues(List<String> serialNumbers, Set<String> seenSerials) {
	    for (String serialNumber : serialNumbers) {
	        if (serialNumber == null || serialNumber.isBlank()) {
	            throw new IllegalArgumentException("Serial number is required");
	        }

	        String cleaned = serialNumber.strip();

	        if (cleaned.length() > 150) {
	            throw new IllegalArgumentException(
	                    "Serial number must be at most 150 characters");
	        }

	        if (!seenSerials.add(cleaned)) {
	            throw new IllegalArgumentException("Duplicate serial number: " + cleaned);
	        }
	    }
	}
	private void validatePurchaseSerials(
	        List<PurchaseItem> items,
	        Map<Long, List<String>> serialsByItemId) {
		if (serialsByItemId == null) {
		    throw new IllegalArgumentException(
		            "Use an empty map when no serial numbers are supplied");
		}

		Set<Long> validItemIds = new HashSet<>();

		for (PurchaseItem item : items) {
		    validItemIds.add(item.getId());
		}

		for (Long submittedItemId : serialsByItemId.keySet()) {
		    if (!validItemIds.contains(submittedItemId)) {
		        throw new IllegalArgumentException(
		                "Line " + submittedItemId + " does not belong to this purchase");
		    }
		}
	    Set<String> seenSerials = new HashSet<>();

	    for (PurchaseItem item : items) {
	    	List<String> serialNumbers =
	                serialsByItemId.getOrDefault(item.getId(), List.of());

	        if (serialNumbers == null) {
	            throw new IllegalArgumentException(
	                    "Use an empty list instead of null for line " + item.getId());
	        }

	        validateSerialCount(item, serialNumbers);
	        validateSerialValues(serialNumbers, seenSerials);
	    }
	    for (String serialNumber : seenSerials) {
	        if (productUnitRepository.existsBySerialNumber(serialNumber)) {
	            throw new IllegalArgumentException(
	                    "Serial number is already registered: " + serialNumber);
	        }
	    }
	}
}
