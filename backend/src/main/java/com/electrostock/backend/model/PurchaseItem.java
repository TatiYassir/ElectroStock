package com.electrostock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import jakarta.persistence.Column;

@Entity
@Table(name = "purchase_items")
public class PurchaseItem {
	//Attributes :
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(optional = false)
	@JoinColumn(name = "purchase_id" , nullable = false)
	private Purchase purchase;
	@ManyToOne(optional = false)
	@JoinColumn(name = "product_id" , nullable = false)
	private Product product;
	@Column(name = "quantity" , nullable = false)
	private int quantity;
	@Column(name = "unit_price_currency" ,nullable = false,
			precision = 19 , scale = 4)
	private BigDecimal unitPriceCurrency;
	@Column(name ="unit_cost_mad_snapshot" ,precision = 19,scale =6)
	private BigDecimal unitCostMadSnapshot;
	@Column(name = "line_total_currency", nullable = false,
			precision = 19, scale = 4)
	private BigDecimal lineTotalCurrency;
	@Column(name = "line_total_mad", precision = 19, scale = 4)
	private BigDecimal lineTotalMad;
	//CONSTRUTORS :
	//NO-ARGUMENT CONSTRUCTOR for JPA:
	protected PurchaseItem() {
		
	}
	//ARGUMENT CONSTRUCTOR :
	public PurchaseItem(Purchase purchase, Product product,
            int quantity, BigDecimal unitPriceCurrency) {
		if (purchase == null) {
			throw new IllegalArgumentException("Purchase cannot be null");
		}
		if (product == null) {
			throw new IllegalArgumentException("Product cannot be null");
}
		if (quantity <= 0) {
			throw new IllegalArgumentException("Quantity must be greater than zero");
		}
		if (unitPriceCurrency == null
				|| unitPriceCurrency.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Unit price must be nonnegative");
		}

		this.purchase = purchase;
		this.product = product;
		this.quantity = quantity;
		this.unitPriceCurrency = unitPriceCurrency;
		this.lineTotalCurrency =
				unitPriceCurrency.multiply(BigDecimal.valueOf(quantity));
	}
	//Getters:
	public Long getId() {
		return id;
	}
	public Purchase getPurchase() {
		return purchase;
	}
	public Product getProduct() {
		return product;
	}
	public int getQuantity() {
		return quantity;
	}
	public BigDecimal getUnitPriceCurrency() {
		return unitPriceCurrency;
	}
	public BigDecimal getUnitCostMadSnapshot() {
		return unitCostMadSnapshot;
	}
	public BigDecimal getLineTotalCurrency() {
		return lineTotalCurrency;
	}
	public BigDecimal getLineTotalMad() {
		return lineTotalMad;
	}
	//Methods:
	public void snapshotCostsInMad() {
	    if (!"MAD".equals(purchase.getCurrency().getCode())) {
	        throw new IllegalStateException(
	                "Only MAD purchase lines can be copied without conversion");
	    }

	    this.unitCostMadSnapshot = this.unitPriceCurrency;
	    this.lineTotalMad = this.lineTotalCurrency;
	}
	public void snapshotCostsInMad(BigDecimal rateToMad) {
	    if (rateToMad == null || rateToMad.signum() <= 0) {
	        throw new IllegalArgumentException("Exchange rate to MAD must be positive");
	    }

	    this.unitCostMadSnapshot = this.unitPriceCurrency
	            .multiply(rateToMad)
	            .setScale(4, RoundingMode.HALF_UP);

	    if (getProduct().isTracksSerials()) {
	        // Each serial gets its own stock movement at the rounded unit cost.
	        this.lineTotalMad = this.unitCostMadSnapshot
	                .multiply(BigDecimal.valueOf(getQuantity()));
	    } else {
	        // One stock movement covers the whole line, so round the line once.
	        this.lineTotalMad = this.lineTotalCurrency
	                .multiply(rateToMad)
	                .setScale(4, RoundingMode.HALF_UP);
	    }
	}
}
