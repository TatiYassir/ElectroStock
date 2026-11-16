package com.electrostock.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//Add alongside the existing imports
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

import com.electrostock.backend.model.Currency;
import com.electrostock.backend.repository.CurrencyRepository;

//Add these alongside your existing imports
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.enums.MovementType;
import com.electrostock.backend.enums.PurchaseStatus;
import com.electrostock.backend.enums.UnitStatus;
import com.electrostock.backend.model.Purchase;
import com.electrostock.backend.model.Supplier;
import com.electrostock.backend.repository.PurchaseRepository;
import com.electrostock.backend.repository.StockMovementRepository;
import com.electrostock.backend.repository.SupplierRepository;

import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.ProductUnit;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.ProductUnitRepository;
import com.electrostock.backend.repository.PurchaseItemRepository;
import com.electrostock.backend.model.PurchaseItem;
import com.electrostock.backend.model.StockMovement;
import com.electrostock.backend.service.PurchaseService;
@SpringBootTest
class BackendApplicationTests {
	// Add inside BackendApplicationTests
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductUnitRepository productUnitRepository;
	@Autowired
	private PurchaseItemRepository purchaseItemRepository;
	@Autowired
	private CurrencyRepository currencyRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private PurchaseRepository purchaseRepository;
	@Autowired
	private PurchaseService purchaseService;
	@PersistenceContext
	private EntityManager entityManager;
	@Autowired
	private StockMovementRepository stockMovementRepository;
	@Test
	@Transactional
	void savesAndReloadsPurchaseItem() {
	    Currency mad = currencyRepository.findByCode("MAD")
	            .orElseThrow(() -> new AssertionError("MAD was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Purchase Item Test Supplier", null, null, null));

	    Product product = productRepository.findAll().stream()
	            .findFirst()
	            .orElseThrow(() -> new AssertionError(
	                    "Add a product to the local database before running this test"));

	    Purchase draft = purchaseRepository.save(
	            new Purchase(supplier, mad, LocalDate.of(2026, 11, 15)));

	    Long purchaseId = draft.getId();
	    Long productId = product.getId();

	    PurchaseItem item = new PurchaseItem(draft, product, 3, new BigDecimal("7000.00"));
	    Long itemId = purchaseItemRepository.saveAndFlush(item).getId();

	    entityManager.clear();

	    PurchaseItem reloadedItem = purchaseItemRepository.findById(itemId)
	            .orElseThrow(() -> new AssertionError("Saved purchase item was not found"));

	    assertEquals(purchaseId, reloadedItem.getPurchase().getId());
	    assertEquals(productId, reloadedItem.getProduct().getId());
	    assertEquals(3, reloadedItem.getQuantity());
	    assertEquals(0, new BigDecimal("21000.00").compareTo(reloadedItem.getLineTotalCurrency()));
	    assertNull(reloadedItem.getUnitCostMadSnapshot());
	    assertNull(reloadedItem.getLineTotalMad());
	    }

	@Test
	void findsSeededMadCurrency() {
	    Currency mad = currencyRepository.findByCode("MAD")
	            .orElseThrow(() -> new AssertionError("MAD was not found in the database"));

	    assertEquals("MAD", mad.getCode());
	}
	// Add these inside BackendApplicationTests.
	// Keep your existing currencyRepository field and both tests.
	@Test
	@Transactional
	void savesAndReloadsEmptyMadDraft() {
	    Currency mad = currencyRepository.findByCode("MAD")
	            .orElseThrow(() -> new AssertionError("MAD was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Purchase Test Supplier", null, null, null));

	    LocalDate purchaseDate = LocalDate.of(2026, 11, 15);
	    Purchase draft = new Purchase(supplier, mad, purchaseDate);

	    Long purchaseId = purchaseRepository.saveAndFlush(draft).getId();
	    assertNotNull(purchaseId);

	    // Clear JPA's managed objects so the next lookup reloads the draft.
	    entityManager.clear();

	    Purchase reloaded = purchaseRepository.findById(purchaseId)
	            .orElseThrow(() -> new AssertionError("Saved draft was not found"));

	    assertEquals(PurchaseStatus.DRAFT, reloaded.getStatus());
	    assertEquals(supplier.getId(), reloaded.getSupplier().getId());
	    assertEquals("MAD", reloaded.getCurrency().getCode());
	    assertEquals(purchaseDate, reloaded.getPurchaseDate());
	    assertNotNull(reloaded.getCreatedAt());
	    assertEquals(0, BigDecimal.ZERO.compareTo(reloaded.getTotalCurrency()));
	    assertNull(reloaded.getRateToMadSnapshot());
	    assertNull(reloaded.getTotalMad());
	    assertNull(reloaded.getConfirmedAt());
	    assertNull(reloaded.getCancelledAt());
	}
	@Test
	@Transactional
	void addingItemUpdatesPurchaseTotal() {

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Purchase Service Test Supplier", null, null, null));

	    Product product = productRepository.findAll().stream()
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Add a product before running this test"));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 15));

	    Long purchaseId = draft.getId();

	    assertEquals(PurchaseStatus.DRAFT, draft.getStatus());
	    assertEquals(0, BigDecimal.ZERO.compareTo(draft.getTotalCurrency()));

	    PurchaseItem item = purchaseService.addItem(
	            purchaseId, product.getId(), 3, new BigDecimal("7000.00"));
	    Long itemId = item.getId();

	    // Send pending changes to the database, then discard the in-memory copies.
	    entityManager.flush();
	    entityManager.clear();

	    Purchase reloadedPurchase = purchaseRepository.findById(purchaseId).orElseThrow();
	    PurchaseItem reloadedItem = purchaseItemRepository.findById(itemId).orElseThrow();

	    BigDecimal expected = new BigDecimal("21000.00");
	    assertEquals(purchaseId, reloadedItem.getPurchase().getId());
	    assertEquals(0, expected.compareTo(reloadedItem.getLineTotalCurrency()));
	    assertEquals(0, expected.compareTo(reloadedPurchase.getTotalCurrency()));
	    assertEquals(PurchaseStatus.DRAFT, reloadedPurchase.getStatus());
	}
	@Test
	@Transactional
	void savesAndReloadsOpeningStockUnit() {
	    Product product = productRepository.findAll().stream()
	            .filter(p -> "MON-001".equals(p.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-001 was not found"));

	    Long productId = product.getId();
	    String serial = "TEST-" + UUID.randomUUID();
	    BigDecimal cost = new BigDecimal("1000.00");

	    ProductUnit unit = new ProductUnit(product, null, "  " + serial + "  ", cost);
	    Long unitId = productUnitRepository.saveAndFlush(unit).getId();

	    entityManager.clear();

	    ProductUnit reloaded = productUnitRepository.findById(unitId).orElseThrow();

	    assertEquals(productId, reloaded.getProduct().getId());
	    assertNull(reloaded.getPurchaseItem());
	    assertEquals(serial, reloaded.getSerialNumber());
	    assertEquals(0, cost.compareTo(reloaded.getAcquisitionCostMad()));
	    assertEquals(UnitStatus.IN_STOCK, reloaded.getStatus());
	    assertNull(reloaded.getWarrantyStart());
	    assertNull(reloaded.getWarrantyEnd());
	}
	@Test
	@Transactional
	void savesAndReloadsUnitLinkedToPurchaseItem() {
	    Product product = productRepository.findAll().stream()
	            .filter(p -> "MON-001".equals(p.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-001 was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Product Unit Test Supplier", null, null, null));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 15));

	    BigDecimal cost = new BigDecimal("1000.00");
	    PurchaseItem savedItem = purchaseService.addItem(
	            draft.getId(), product.getId(), 1, cost);
	    Long savedItemId = savedItem.getId();

	    String serial = "TEST-" + UUID.randomUUID();
	    ProductUnit unit = new ProductUnit(
	            savedItem.getProduct(), savedItem, serial, cost);
	    Long unitId = productUnitRepository.saveAndFlush(unit).getId();

	    entityManager.clear();

	    ProductUnit reloaded = productUnitRepository.findById(unitId).orElseThrow();

	    assertNotNull(reloaded.getPurchaseItem());
	    assertEquals(savedItemId, reloaded.getPurchaseItem().getId());
	    assertEquals(product.getId(), reloaded.getProduct().getId());
	}
	@Test
	@Transactional
	void savesAndReloadsPurchaseMovementWithoutSerialTracking() {
	    Product product = productRepository.findAll().stream()
	            .filter(p -> "MON-002".equals(p.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-002 was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Stock Movement Test Supplier", null, null, null));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 16));

	    PurchaseItem item = purchaseService.addItem(
	            draft.getId(), product.getId(), 3, new BigDecimal("1000.00"));

	    Long productId = product.getId();
	    Long itemId = item.getId();
	    BigDecimal value = new BigDecimal("3000.00");
	    LocalDateTime movementAt = LocalDateTime.of(2026, 11, 16, 10, 0);

	    StockMovement movement =
	            StockMovement.forPurchase(item, null, 3, value, movementAt);
	    Long movementId = stockMovementRepository.saveAndFlush(movement).getId();

	    entityManager.clear();

	    StockMovement reloaded = stockMovementRepository.findById(movementId)
	            .orElseThrow();

	    assertEquals(productId, reloaded.getProduct().getId());
	    assertEquals(itemId, reloaded.getPurchaseItem().getId());
	    assertNull(reloaded.getProductUnit());
	    assertEquals(MovementType.PURCHASE, reloaded.getMovementType());
	    assertEquals(3, reloaded.getQuantityChange());
	    assertEquals(0, value.compareTo(reloaded.getValueChangeMad()));
	    assertEquals(movementAt, reloaded.getMovementAt());
	}
	@Test
	@Transactional
	void savesAndReloadsPurchaseMovementWithSerialTracking() {
	    Product product = productRepository.findAll().stream()
	            .filter(p -> "MON-001".equals(p.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-001 was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier(" Serial Movement Test Supplier".strip(), null, null, null));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 16));

	    BigDecimal cost = new BigDecimal("1000.00");
	    PurchaseItem item = purchaseService.addItem(
	            draft.getId(), product.getId(), 1, cost);

	    String serial = "TEST-" + UUID.randomUUID();
	    ProductUnit savedUnit = productUnitRepository.saveAndFlush(
	            new ProductUnit(product, item, serial, cost));

	    Long productId = product.getId();
	    Long itemId = item.getId();
	    Long unitId = savedUnit.getId();
	    LocalDateTime movementAt = LocalDateTime.of(2026, 11, 16, 10, 0);

	    StockMovement movement =
	            StockMovement.forPurchase(item, savedUnit, 1, cost, movementAt);
	    Long movementId = stockMovementRepository.saveAndFlush(movement).getId();

	    entityManager.clear();

	    StockMovement reloaded = stockMovementRepository.findById(movementId)
	            .orElseThrow();

	    assertEquals(productId, reloaded.getProduct().getId());
	    assertEquals(itemId, reloaded.getPurchaseItem().getId());
	    assertNotNull(reloaded.getProductUnit());
	    assertEquals(unitId, reloaded.getProductUnit().getId());
	    assertEquals(MovementType.PURCHASE, reloaded.getMovementType());
	    assertEquals(1, reloaded.getQuantityChange());
	    assertEquals(0, cost.compareTo(reloaded.getValueChangeMad()));
	    assertEquals(movementAt, reloaded.getMovementAt());
	}
	@Test
	@Transactional
	void rejectsTooFewSerialNumbersForTrackedPurchaseItem() {
	    Product product = productRepository.findAll().stream()
	            .filter(p -> "MON-001".equals(p.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-001 was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Serial Count Test Supplier", null, null, null));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 16));

	    PurchaseItem item = purchaseService.addItem(
	            draft.getId(), product.getId(), 3, new BigDecimal("1000.00"));

	    IllegalArgumentException error = assertThrows(
	            IllegalArgumentException.class,
	            () -> purchaseService.validatePurchaseConfirmation(
	                    draft.getId(),
	                    Map.of(item.getId(), List.of("SN-A", "SN-B"))));

	    assertEquals("Expected 3 serial numbers for MON-001, but got 2",
	            error.getMessage());
	}
	@Test
	@Transactional
	void acceptsThreeDistinctSerialNumbersForTrackedPurchaseItem() {
	    Product product = productRepository.findAll().stream()
	            .filter(p -> "MON-001".equals(p.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-001 was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Valid Serials Test Supplier", null, null, null));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 16));

	    PurchaseItem item = purchaseService.addItem(
	            draft.getId(), product.getId(), 3, new BigDecimal("1000.00"));

	    String prefix = "TEST-" + UUID.randomUUID();

	    purchaseService.validatePurchaseConfirmation(
	            draft.getId(),
	            Map.of(item.getId(), List.of(
	                    prefix + "-A",
	                    prefix + "-B",
	                    prefix + "-C")));

	    assertEquals(PurchaseStatus.DRAFT, draft.getStatus());
	}
	@Test
	void contextLoads() {
	}

}
