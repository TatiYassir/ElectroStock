package com.electrostock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;

import com.electrostock.backend.enums.AdjustmentReason;
import com.electrostock.backend.enums.UnitStatus;

@Entity
@Table(name ="product_units")
public class ProductUnit {
	//Attributes :
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(optional = false)
	@JoinColumn(name ="product_id", nullable = false)
	private Product product;
	@ManyToOne(optional = true)
	@JoinColumn(name ="purchase_item_id")
	private PurchaseItem purchaseItem;
	@Column(name = "serial_number", nullable = false , unique = true, length = 150)
	private String serialNumber;
	@Column(name = "acquisition_cost_mad", nullable = false , 
			precision = 19, scale = 4)
	private BigDecimal acquisitionCostMad;
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private UnitStatus status = UnitStatus.IN_STOCK;
	@Column(name = "warranty_start")
	private LocalDate warrantyStart;
	@Column(name = "warranty_end")
	private LocalDate warrantyEnd;
	//NO-ARGUMNET CONSTRUCTOR for JPA :
	protected ProductUnit() {
		
	}
	//Argument Constructor :
	public ProductUnit(Product product, PurchaseItem purchaseItem,
            String serialNumber, BigDecimal acquisitionCostMad) {
			if (product == null) {
				throw new IllegalArgumentException("Product is required");
			}

			if (serialNumber == null || serialNumber.isBlank()) {
				throw new IllegalArgumentException("Serial number is required");
			}

			String normalizedSerialNumber = serialNumber.strip();
			if (normalizedSerialNumber.length() > 150) {
				throw new IllegalArgumentException(
						"Serial number must be at most 150 characters");
			}

			if (acquisitionCostMad == null
					|| acquisitionCostMad.compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException(
						"Acquisition cost is required and cannot be negative");
			}

			this.product = product;
			this.purchaseItem = purchaseItem;
			this.serialNumber = normalizedSerialNumber;
			this.acquisitionCostMad = acquisitionCostMad;
	}
	//Getters:
	public Long getId() {
		return id;
	}
	public Product getProduct() {
		return product;
	}
	public PurchaseItem getPurchaseItem() {
		return purchaseItem;
	}
	public String getSerialNumber() {
		return serialNumber;
	}
	public BigDecimal getAcquisitionCostMad() {
		return acquisitionCostMad;
	}
	public UnitStatus getStatus() {
		return status;
	}
	public LocalDate getWarrantyStart() {
		return warrantyStart;
	}
	public LocalDate getWarrantyEnd() {
		return warrantyEnd;
	}
	public void markSold() {
	    if (status != UnitStatus.IN_STOCK) {
	        throw new IllegalStateException("Only in-stock units can be marked as sold");
	    }

	    this.status = UnitStatus.SOLD;
	}
	public void startWarrantyAtHandover(LocalDate handoverDate) {
	    if (handoverDate == null) {
	        throw new IllegalArgumentException("Handover date is required");
	    }

	    if (status != UnitStatus.SOLD) {
	        throw new IllegalStateException("Only sold units can start a warranty");
	    }

	    if (warrantyStart != null || warrantyEnd != null) {
	        throw new IllegalStateException("Warranty dates have already been set");
	    }

	    int warrantyMonths = product.getWarrantyMonths();
	    if (warrantyMonths == 0) {
	        return; // No warranty configured. Leave both dates unset.
	    }

	    this.warrantyStart = handoverDate;
	    this.warrantyEnd = handoverDate.plusMonths(warrantyMonths);
	}
	public void returnToStockAfterSaleCancellation() {
	    if (status != UnitStatus.SOLD) {
	        throw new IllegalStateException("Only sold units can be returned to stock");
	    }

	    if (warrantyStart != null || warrantyEnd != null) {
	        throw new IllegalStateException(
	                "Cannot return a unit to stock after its warranty has started");
	    }

	    this.status = UnitStatus.IN_STOCK;
	}
	public void voidForPurchaseCancellation() {
	    if (status != UnitStatus.IN_STOCK) {
	        throw new IllegalStateException(
	                "Cannot cancel purchase: unit " + serialNumber + " is not in stock");
	    }

	    this.status = UnitStatus.VOIDED;
	}
	public void removeFromStockForAdjustment(AdjustmentReason reason) {
	    if (reason == null) {
	        throw new IllegalArgumentException("Adjustment reason is required");
	    }

	    if (status != UnitStatus.IN_STOCK) {
	        throw new IllegalStateException(
	                "Cannot adjust unit " + serialNumber + ": it is not in stock");
	    }

	    this.status = reason == AdjustmentReason.MISSING
	            ? UnitStatus.LOST
	            : UnitStatus.VOIDED;
	}
	public void restoreLostUnitToStock() {
	    if (status != UnitStatus.LOST) {
	        throw new IllegalStateException(
	                "Cannot restore unit " + serialNumber + ": it is not marked LOST");
	    }

	    this.status = UnitStatus.IN_STOCK;
	}
}
