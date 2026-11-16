package com.electrostock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.electrostock.backend.enums.MovementType;

import jakarta.persistence.Column;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
	//Attributes:
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(optional = false)
	@JoinColumn(name= "product_id" , nullable = false)
	private Product product;
	@ManyToOne(optional = true)
	@JoinColumn(name = "product_unit_id")
	private ProductUnit productUnit;
	@ManyToOne(optional = true)
	@JoinColumn(name = "purchase_item_id")
	private PurchaseItem purchaseItem;
	@ManyToOne
	@JoinColumn(name = "sale_item_id")
	private SaleItem saleItem;
	@Column(name = "quantity_change" , nullable = false)
	private int quantityChange;
	@Enumerated(EnumType.STRING)
	@Column(name = "movement_type" , nullable = false,length = 25)
	private MovementType movementType;
	@Column(name = "value_change_mad" , nullable = false,precision = 19,scale = 4)
	private BigDecimal valueChangeMad;
	@Column(name = "movement_at" , nullable = false)
	private LocalDateTime movementAt;
	@OneToOne
	@JoinColumn(name = "reversal_of_movement_id", unique = true)
	private StockMovement reversalOfMovement;
	//NO-ARGUMNET CONSTRUCTOR for JPA:
	protected StockMovement() {
		
	}
	//Getters :
		public Long getId() {
			return id;
		}
		public Product getProduct() {
			return product;
		}
		public ProductUnit getProductUnit() {
			return productUnit;
		}
		public PurchaseItem getPurchaseItem() {
			return purchaseItem;
		}
		public int getQuantityChange() {
			return quantityChange;
		}
		public MovementType getMovementType() {
			return movementType;
		}
		public BigDecimal getValueChangeMad() {
			return valueChangeMad;
		}
		public LocalDateTime getMovementAt() {
			return movementAt;
		}
		public SaleItem getSaleItem() {
		    return saleItem;
		}
		public StockMovement getReversalOfMovement() {
		    return reversalOfMovement;
		}
	//Methods:
	public static StockMovement forPurchase(
	        PurchaseItem purchaseItem,
	        ProductUnit productUnit,
	        int quantityChange,
	        BigDecimal valueChangeMad,
	        LocalDateTime movementAt) {

	    if (purchaseItem == null) {
	        throw new IllegalArgumentException("Purchase item is required");
	    }
	    if (quantityChange <= 0) {
	        throw new IllegalArgumentException("Quantity change must be positive");
	    }
	    if (productUnit != null && quantityChange != 1) {
	        throw new IllegalArgumentException(
	                "A movement for one physical unit must add exactly one");
	    }
	    if (valueChangeMad == null || valueChangeMad.signum() < 0) {
	        throw new IllegalArgumentException(
	                "Value change is required and cannot be negative");
	    }
	    if (movementAt == null) {
	        throw new IllegalArgumentException("Movement time is required");
	    }

	    StockMovement movement = new StockMovement();
	    movement.purchaseItem = purchaseItem;
	    movement.product = purchaseItem.getProduct();
	    movement.productUnit = productUnit;
	    movement.movementType = MovementType.PURCHASE;
	    movement.quantityChange = quantityChange;
	    movement.valueChangeMad = valueChangeMad;
	    movement.movementAt = movementAt;
	    return movement;
	}
	public static StockMovement forSale(
	        SaleItem saleItem,
	        ProductUnit productUnit,
	        int quantitySold,
	        BigDecimal inventoryCostMad,
	        LocalDateTime movementAt) {

	    if (saleItem == null) {
	        throw new IllegalArgumentException("Sale item is required");
	    }
	    if (quantitySold <= 0) {
	        throw new IllegalArgumentException("Quantity sold must be positive");
	    }
	    if (productUnit != null && quantitySold != 1) {
	        throw new IllegalArgumentException(
	                "A movement for one physical unit must remove exactly one");
	    }
	    if (inventoryCostMad == null || inventoryCostMad.signum() < 0) {
	        throw new IllegalArgumentException(
	                "Inventory cost is required and cannot be negative");
	    }
	    if (movementAt == null) {
	        throw new IllegalArgumentException("Movement time is required");
	    }

	    StockMovement movement = new StockMovement();
	    movement.saleItem = saleItem;
	    movement.product = saleItem.getProduct();
	    movement.productUnit = productUnit;
	    movement.movementType = MovementType.SALE;
	    movement.quantityChange = -quantitySold;
	    movement.valueChangeMad = inventoryCostMad.negate();
	    movement.movementAt = movementAt;
	    return movement;
	}
	public static StockMovement reverseSale(
	        StockMovement original, LocalDateTime movementAt) {

	    if (original == null || original.getId() == null
	            || original.getMovementType() != MovementType.SALE) {
	        throw new IllegalArgumentException("A saved sale movement is required");
	    }

	    if (movementAt == null || movementAt.isBefore(original.getMovementAt())) {
	        throw new IllegalArgumentException(
	                "Reversal time must be at or after the original movement");
	    }

	    StockMovement reversal = new StockMovement();
	    reversal.reversalOfMovement = original;
	    reversal.product = original.product;
	    reversal.productUnit = original.productUnit;
	    reversal.movementType = MovementType.REVERSAL;
	    reversal.quantityChange = -original.quantityChange;
	    reversal.valueChangeMad = original.valueChangeMad.negate();
	    reversal.movementAt = movementAt;
	    return reversal;
	}
	public static StockMovement reversePurchase(
	        StockMovement original, LocalDateTime movementAt) {

	    if (original == null || original.getId() == null
	            || original.getMovementType() != MovementType.PURCHASE) {
	        throw new IllegalArgumentException("A saved purchase movement is required");
	    }

	    if (movementAt == null || movementAt.isBefore(original.getMovementAt())) {
	        throw new IllegalArgumentException(
	                "Reversal time must be at or after the original movement");
	    }

	    StockMovement reversal = new StockMovement();
	    reversal.reversalOfMovement = original;
	    reversal.product = original.product;
	    reversal.productUnit = original.productUnit;
	    reversal.movementType = MovementType.REVERSAL;
	    reversal.quantityChange = -original.quantityChange;
	    reversal.valueChangeMad = original.valueChangeMad.negate();
	    reversal.movementAt = movementAt;
	    return reversal;
	}
	public static StockMovement forOpeningStock(
	        Product product,
	        ProductUnit productUnit,
	        int quantityAdded,
	        BigDecimal inventoryValueMad,
	        LocalDateTime movementAt) {

	    if (product == null) {
	        throw new IllegalArgumentException("Product is required");
	    }
	    if (quantityAdded <= 0) {
	        throw new IllegalArgumentException("Opening quantity must be positive");
	    }
	    if (product.isTracksSerials() && (productUnit == null || quantityAdded != 1)) {
	        throw new IllegalArgumentException(
	                "Opening stock for a tracked product requires one unit per movement");
	    }
	    if (!product.isTracksSerials() && productUnit != null) {
	        throw new IllegalArgumentException(
	                "Untracked products cannot have a product unit");
	    }
	    if (productUnit != null && !product.getId().equals(productUnit.getProduct().getId())) {
	        throw new IllegalArgumentException("Unit must belong to the selected product");
	    }
	    if (inventoryValueMad == null || inventoryValueMad.signum() < 0) {
	        throw new IllegalArgumentException(
	                "Inventory value is required and cannot be negative");
	    }
	    if (movementAt == null) {
	        throw new IllegalArgumentException("Movement time is required");
	    }

	    StockMovement movement = new StockMovement();
	    movement.product = product;
	    movement.productUnit = productUnit;
	    movement.movementType = MovementType.OPENING_STOCK;
	    movement.quantityChange = quantityAdded;
	    movement.valueChangeMad = inventoryValueMad;
	    movement.movementAt = movementAt;
	    return movement;
	}
}
