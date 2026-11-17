package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.exception.PurchaseCancellationNotAllowedException;
import com.electrostock.backend.exception.SupplierNotFoundException;
import com.electrostock.backend.exception.product.ProductNotFoundException;
import com.electrostock.backend.model.Currency;
import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.ProductUnit;
import com.electrostock.backend.model.Purchase;
import com.electrostock.backend.model.PurchaseItem;
import com.electrostock.backend.model.StockMovement;
import com.electrostock.backend.model.Supplier;
import com.electrostock.backend.enums.MovementType;
import com.electrostock.backend.enums.PurchaseStatus;
import com.electrostock.backend.enums.UnitStatus;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.ProductUnitRepository;
import com.electrostock.backend.repository.PurchaseRepository;
import com.electrostock.backend.repository.PurchaseItemRepository;
import com.electrostock.backend.repository.SupplierRepository;
import com.electrostock.backend.repository.CurrencyRepository;
import com.electrostock.backend.repository.StockMovementRepository;
import com.electrostock.backend.dto.purchase.PurchaseItemResponse;
import com.electrostock.backend.dto.purchase.PurchaseResponse;

@Service
public class PurchaseService {
	//Attributes :
	private final PurchaseRepository purchaseRepository;
	private final ProductRepository productRepository;
	private final PurchaseItemRepository purchaseItemRepository;
	private final SupplierRepository supplierRepository;
	private final CurrencyRepository currencyRepository;
	private final ProductUnitRepository productUnitRepository;
	private final StockMovementRepository stockMovementRepository;
	private final CostingService costingService;
	private final ExchangeRateService exchangeRateService;
	//Constructor :
	public PurchaseService(PurchaseRepository purchaseRepository,
	                       ProductRepository productRepository,
	                       PurchaseItemRepository purchaseItemRepository,
	                       SupplierRepository supplierRepository,
	                       CurrencyRepository currencyRepository,
	                       ProductUnitRepository productUnitRepository,
	                       StockMovementRepository stockMovementRepository,
	                       CostingService costingService,
	                       ExchangeRateService exchangeRateService) {
	    this.purchaseRepository = purchaseRepository;
	    this.productRepository = productRepository;
	    this.purchaseItemRepository = purchaseItemRepository;
	    this.supplierRepository = supplierRepository;
	    this.currencyRepository = currencyRepository;
	    this.productUnitRepository = productUnitRepository;
	    this.stockMovementRepository = stockMovementRepository;
	    this.costingService = costingService;
	    this.exchangeRateService = exchangeRateService;
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
	            purchase.getTotalMad(),
	            purchase.getRateToMadSnapshot(),
	            purchase.getRateSourceSnapshot(),
	            purchase.getRateRetrievedAtSnapshot(),
	            purchase.isRateFromCacheSnapshot(),
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
	@Transactional
	public void confirmPurchaseInMad(
	        Long purchaseId, Map<Long, List<String>> serialsByItemId) {

	    Purchase purchase = purchaseRepository.findById(purchaseId)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Purchase not found: " + purchaseId));

	    List<PurchaseItem> items =
	            purchaseItemRepository.findByPurchase_Id(purchaseId);

	    validateConfirmation(purchase, items);
	    validatePurchaseSerials(items, serialsByItemId);

	    LocalDateTime confirmedAt = LocalDateTime.now();
	    
	    ExchangeRateQuote quote =
	            exchangeRateService.getRateToMad(
	                    purchase.getCurrency().getCode());

	    BigDecimal rateToMad = quote.rateToMad();
	    BigDecimal totalMad = BigDecimal.ZERO;
	    for (PurchaseItem item : items) {
	        item.snapshotCostsInMad(rateToMad);
	        totalMad = totalMad.add(item.getLineTotalMad());

	        Product product = item.getProduct();

	        if (product.isTracksSerials()) {
	            for (String serialNumber : serialsByItemId.get(item.getId())) {
	                ProductUnit unit = productUnitRepository.save(
	                        new ProductUnit(product, item, serialNumber,
	                                item.getUnitCostMadSnapshot()));

	                stockMovementRepository.save(StockMovement.forPurchase(
	                        item, unit, 1, item.getUnitCostMadSnapshot(), confirmedAt));
	            }
	        } else {
	            stockMovementRepository.save(StockMovement.forPurchase(
	                    item, null, item.getQuantity(),
	                    item.getLineTotalMad(), confirmedAt));

	            BigDecimal averageCost =
	                    costingService.calculateAverageCostMad(product.getId());

	            product.updateAverageCostMad(averageCost);
	        }
	    }

	    purchase.confirmWithRate(
	            rateToMad,
	            totalMad,
	            quote.source(),
	            quote.retrievedAt(),
	            quote.fromCache(),
	            confirmedAt);
	}
	private void validateStockForPurchaseCancellation(List<StockMovement> movements) {
	    if (movements.isEmpty()) {
	        throw new IllegalStateException("No stock movements found for this purchase");
	    }

	    Map<Long, Long> quantityToRemove = new HashMap<>();
	    Map<Long, BigDecimal> valueToRemove = new HashMap<>();

	    for (StockMovement movement : movements) {
	        ProductUnit unit = movement.getProductUnit();

	        if (unit != null && unit.getStatus() != UnitStatus.IN_STOCK) {
	            throw new PurchaseCancellationNotAllowedException(
	                    "Cannot cancel purchase: unit " + unit.getSerialNumber()
	                            + " is not in stock");
	        }

	        Long productId = movement.getProduct().getId();

	        quantityToRemove.merge(
	                productId, (long) movement.getQuantityChange(), Long::sum);
	        valueToRemove.merge(
	                productId, movement.getValueChangeMad(), BigDecimal::add);
	    }

	    for (Map.Entry<Long, Long> entry : quantityToRemove.entrySet()) {
	        Long productId = entry.getKey();
	        long remainingQuantity =
	                stockMovementRepository.getStockQuantity(productId) - entry.getValue();

	        BigDecimal remainingValue = stockMovementRepository.getStockValueMad(productId)
	                .subtract(valueToRemove.get(productId));

	        if (remainingQuantity < 0 || remainingValue.signum() < 0
	                || (remainingQuantity == 0 && remainingValue.signum() != 0)) {
	            throw new PurchaseCancellationNotAllowedException(
	                    "Cannot cancel purchase: reversing receipts would leave "
	                            + "an invalid stock balance for product " + productId);
	        }
	    }
	}
	@Transactional
	public void cancelPurchase(Long purchaseId) {
	    Purchase purchase = purchaseRepository.findById(purchaseId)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Purchase not found: " + purchaseId));

	    if (purchase.getStatus() != PurchaseStatus.CONFIRMED) {
	        throw new PurchaseCancellationNotAllowedException(
	        		"Only confirmed purchases can be cancelled");
	    }

	    List<StockMovement> originalMovements =
	            stockMovementRepository.findByPurchaseItem_Purchase_IdAndMovementType(
	                    purchaseId, MovementType.PURCHASE);

	    validateStockForPurchaseCancellation(originalMovements);

	    LocalDateTime cancelledAt = LocalDateTime.now();
	    Map<Long, Product> untrackedProducts = new HashMap<>();

	    for (StockMovement original : originalMovements) {
	        ProductUnit unit = original.getProductUnit();
	        Product product = original.getProduct();

	        if (unit != null) {
	            unit.voidForPurchaseCancellation();
	        }

	        if (!product.isTracksSerials()) {
	            untrackedProducts.put(product.getId(), product);
	        }

	        stockMovementRepository.save(
	                StockMovement.reversePurchase(original, cancelledAt));
	    }

	    stockMovementRepository.flush();

	    for (Product product : untrackedProducts.values()) {
	        long remainingQuantity =
	                stockMovementRepository.getStockQuantity(product.getId());

	        if (remainingQuantity == 0) {
	            product.clearAverageCostMad();
	        } else {
	            product.updateAverageCostMad(
	                    costingService.calculateAverageCostMad(product.getId()));
	        }
	    }

	    purchase.cancelAfterConfirmation(cancelledAt);
	}
}
