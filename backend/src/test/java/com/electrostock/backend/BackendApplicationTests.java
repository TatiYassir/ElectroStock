package com.electrostock.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//Add alongside the existing imports
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.beans.factory.annotation.Autowired;

import com.electrostock.backend.model.Currency;
import com.electrostock.backend.repository.CurrencyRepository;

//Add these alongside your existing imports
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.enums.PurchaseStatus;
import com.electrostock.backend.model.Purchase;
import com.electrostock.backend.model.Supplier;
import com.electrostock.backend.repository.PurchaseRepository;
import com.electrostock.backend.repository.SupplierRepository;

import com.electrostock.backend.model.Product;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.PurchaseItemRepository;
import com.electrostock.backend.model.PurchaseItem;
import com.electrostock.backend.service.PurchaseService;
@SpringBootTest
class BackendApplicationTests {
	// Add inside BackendApplicationTests
	@Autowired
	private ProductRepository productRepository;

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
	void contextLoads() {
	}

}
