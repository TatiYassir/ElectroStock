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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
	@MockitoSpyBean
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
	@Transactional
	void confirmsMadPurchaseWithTrackedAndUntrackedItems() {
	    List<Product> products = productRepository.findAll();

	    Product tracked = products.stream()
	            .filter(product -> "MON-001".equals(product.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-001 was not found"));

	    Product untracked = products.stream()
	            .filter(product -> "MON-002".equals(product.getSku()))
	            .findFirst()
	            .orElseThrow(() -> new AssertionError("Test product MON-002 was not found"));

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Purchase Confirmation Test Supplier", null, null, null));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 16));
	    Long purchaseId = draft.getId();

	    BigDecimal unitCost = new BigDecimal("1000.00");
	    Long trackedItemId = purchaseService.addItem(
	            purchaseId, tracked.getId(), 2, unitCost).getId();
	    Long untrackedItemId = purchaseService.addItem(
	            purchaseId, untracked.getId(), 3, unitCost).getId();

	    String prefix = "TEST-" + UUID.randomUUID();
	    List<String> serials = List.of(prefix + "-A", prefix + "-B");

	    purchaseService.confirmPurchaseInMad(
	            purchaseId, Map.of(trackedItemId, serials));

	    entityManager.flush();
	    entityManager.clear();

	    // Check the purchase and its saved costs.
	    Purchase confirmed = purchaseRepository.findById(purchaseId).orElseThrow();
	    assertEquals(PurchaseStatus.CONFIRMED, confirmed.getStatus());
	    assertEquals(0, BigDecimal.ONE.compareTo(confirmed.getRateToMadSnapshot()));
	    assertEquals(0, new BigDecimal("5000.00").compareTo(confirmed.getTotalMad()));
	    assertNotNull(confirmed.getConfirmedAt());

	    PurchaseItem trackedItem = purchaseItemRepository.findById(trackedItemId).orElseThrow();
	    PurchaseItem untrackedItem = purchaseItemRepository.findById(untrackedItemId).orElseThrow();
	    assertEquals(0, unitCost.compareTo(trackedItem.getUnitCostMadSnapshot()));
	    assertEquals(0, new BigDecimal("2000.00").compareTo(trackedItem.getLineTotalMad()));
	    assertEquals(0, unitCost.compareTo(untrackedItem.getUnitCostMadSnapshot()));
	    assertEquals(0, new BigDecimal("3000.00").compareTo(untrackedItem.getLineTotalMad()));

	    // Tracked line: two physical units and one movement per unit.
	    List<ProductUnit> units = productUnitRepository.findByPurchaseItem_Id(trackedItemId);
	    assertEquals(serials, units.stream().map(ProductUnit::getSerialNumber).sorted().toList());

	    List<StockMovement> trackedMovements =
	            stockMovementRepository.findByPurchaseItem_Id(trackedItemId);
	    assertEquals(2, trackedMovements.size());

	    for (ProductUnit unit : units) {
	        assertEquals(UnitStatus.IN_STOCK, unit.getStatus());
	        assertEquals(0, unitCost.compareTo(unit.getAcquisitionCostMad()));

	        List<StockMovement> movementsForUnit = trackedMovements.stream()
	                .filter(movement -> movement.getProductUnit() != null
	                        && unit.getId().equals(movement.getProductUnit().getId()))
	                .toList();

	        assertEquals(1, movementsForUnit.size());
	        StockMovement movement = movementsForUnit.get(0);
	        assertEquals(MovementType.PURCHASE, movement.getMovementType());
	        assertEquals(1, movement.getQuantityChange());
	        assertEquals(0, unitCost.compareTo(movement.getValueChangeMad()));
	        assertEquals(confirmed.getConfirmedAt(), movement.getMovementAt());
	    }

	    // Untracked line: no physical units and one movement for all three.
	    assertEquals(0, productUnitRepository.findByPurchaseItem_Id(untrackedItemId).size());

	    List<StockMovement> untrackedMovements =
	            stockMovementRepository.findByPurchaseItem_Id(untrackedItemId);
	    assertEquals(1, untrackedMovements.size());

	    StockMovement movement = untrackedMovements.get(0);
	    assertNull(movement.getProductUnit());
	    assertEquals(MovementType.PURCHASE, movement.getMovementType());
	    assertEquals(3, movement.getQuantityChange());
	    assertEquals(0, new BigDecimal("3000.00").compareTo(movement.getValueChangeMad()));
	    assertEquals(confirmed.getConfirmedAt(), movement.getMovementAt());
	}
	@Test
	void rollsBackMadConfirmationWhenMovementWriteFails() {
	    Product product = productRepository.findAll().stream()
	            .filter(p -> "MON-001".equals(p.getSku()))
	            .findFirst()
	            .orElseThrow();

	    Supplier supplier = supplierRepository.save(
	            new Supplier("Confirmation Rollback Test Supplier", null, null, null));

	    Purchase draft = purchaseService.createDraft(
	            supplier.getId(), "MAD", LocalDate.of(2026, 11, 16));
	    Long purchaseId = draft.getId();

	    PurchaseItem item = purchaseService.addItem(
	            purchaseId, product.getId(), 1, new BigDecimal("1000.00"));
	    Long itemId = item.getId();
	    String serial = "TEST-" + UUID.randomUUID();

	    try {
	        doAnswer(invocation -> {
	            StockMovement movement = invocation.getArgument(0);

	            // Write for real, then fail before confirmation can finish.
	            entityManager.persist(movement);
	            entityManager.flush();

	            throw new IllegalStateException("Forced failure after movement flush");
	        }).when(stockMovementRepository).save(any(StockMovement.class));

	        IllegalStateException error = assertThrows(
	                IllegalStateException.class,
	                () -> purchaseService.confirmPurchaseInMad(
	                        purchaseId, Map.of(itemId, List.of(serial))));

	        assertEquals("Forced failure after movement flush", error.getMessage());

	        Purchase reloaded = purchaseRepository.findById(purchaseId).orElseThrow();
	        assertEquals(PurchaseStatus.DRAFT, reloaded.getStatus());
	        assertNull(reloaded.getConfirmedAt());
	        assertNull(reloaded.getTotalMad());

	        PurchaseItem reloadedItem = purchaseItemRepository.findById(itemId).orElseThrow();
	        assertNull(reloadedItem.getUnitCostMadSnapshot());
	        assertNull(reloadedItem.getLineTotalMad());

	        assertTrue(productUnitRepository.findByPurchaseItem_Id(itemId).isEmpty());
	        assertTrue(stockMovementRepository.findByPurchaseItem_Id(itemId).isEmpty());
	    } finally {
	        // The test setup was saved outside the confirmation transaction.
	        stockMovementRepository.deleteAll(
	                stockMovementRepository.findByPurchaseItem_Id(itemId));
	        productUnitRepository.deleteAll(
	                productUnitRepository.findByPurchaseItem_Id(itemId));
	        purchaseItemRepository.deleteById(itemId);
	        purchaseRepository.deleteById(purchaseId);
	        supplierRepository.deleteById(supplier.getId());
	    }
	}
	@Test
	void contextLoads() {
	}

}
