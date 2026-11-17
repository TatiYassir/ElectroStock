package com.electrostock.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.electrostock.backend.enums.PurchaseStatus;

import jakarta.persistence.JoinColumn;


@Entity
@Table(name = "purchases")
public class Purchase {
	//Attributes :
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(optional = false)
	@JoinColumn(name = "supplier_id",nullable = false)
	private Supplier supplier;
	
	@ManyToOne(optional = false)
	@JoinColumn(name = "currency_id",nullable = false)
	private Currency currency;
	
	@Column(name = "purchase_date", nullable = false)
	private LocalDate purchaseDate;
	
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
	@Enumerated(EnumType.STRING)
	
	@Column(name = "status", nullable = false, length = 20)
	private PurchaseStatus status = PurchaseStatus.DRAFT;
	
	@Column(name = "rate_to_mad_snapshot",
			precision = 20, scale = 10)
	private BigDecimal rateToMadSnapshot;
	
	@Column(name = "rate_source_snapshot", length = 100)
	private String rateSourceSnapshot;
	
	@Column(name = "rate_retrieved_at_snapshot")
	private LocalDateTime rateRetrievedAtSnapshot;
	
	@Column(name = "rate_overridden_snapshot", nullable = false)
	private boolean rateOverriddenSnapshot = false;
	@Column(name = "rate_from_cache_snapshot",
	        nullable = false)
	private boolean rateFromCacheSnapshot = false;
	
	@Column(name = "total_currency", nullable = false, precision = 19, scale = 4)
	private BigDecimal totalCurrency = BigDecimal.ZERO;
	
	@Column(name = "total_mad", precision = 19, scale = 4)
	private BigDecimal totalMad;
	
	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	
	//NO-ARGUMENT CONSTRUCTOR for JPA:
	protected Purchase() {
		
	}
	//Argument Constructor:
	public Purchase(Supplier supplier ,Currency currency,LocalDate purchaseDate) {
		if(supplier == null ) {
			throw new IllegalArgumentException("Supplier cannot be null");
		}
		if(currency == null ) {
			throw new IllegalArgumentException("Currency cannot be null");
		}
		if(purchaseDate == null ) {
			throw new IllegalArgumentException("Purchase date cannot be null");
		}
		this.supplier = supplier;
		this.currency = currency;
		this.purchaseDate = purchaseDate;
	}
	//Getters :
	public Long getId() {
		return id;
	}
	public Supplier getSupplier() {
		return supplier;
	}
	public Currency getCurrency() {
		return currency;
	}
	public LocalDate getPurchaseDate() {
		return purchaseDate;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public PurchaseStatus getStatus() {
		return status;
	}
	public BigDecimal getRateToMadSnapshot() {
		return rateToMadSnapshot;
	}
	public String getRateSourceSnapshot() {
		return rateSourceSnapshot;
	}
	public LocalDateTime getRateRetrievedAtSnapshot() {
		return rateRetrievedAtSnapshot;
	}
	public boolean isRateOverriddenSnapshot() {
		return rateOverriddenSnapshot;
	}
	public BigDecimal getTotalCurrency() {
		return totalCurrency;
	}
	public BigDecimal getTotalMad() {
		return totalMad;
	}
	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}
	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}
	//Methods :
	public PurchaseItem addItem(Product product, int quantity,
            BigDecimal unitPriceCurrency) {
		if (status != PurchaseStatus.DRAFT) {
			throw new IllegalStateException("Items can only be added to a draft purchase");
		}

		PurchaseItem item = new PurchaseItem(this, product, quantity, unitPriceCurrency);
		totalCurrency = totalCurrency.add(item.getLineTotalCurrency());

		return item;
	}
	public void confirmInMad(LocalDateTime confirmedAt) {
	    if (status != PurchaseStatus.DRAFT) {
	        throw new IllegalStateException("Only draft purchases can be confirmed");
	    }

	    if (!"MAD".equals(currency.getCode())) {
	        throw new IllegalStateException("This method only confirms purchases in MAD");
	    }

	    if (confirmedAt == null) {
	        throw new IllegalArgumentException("Confirmation time is required");
	    }

	    this.rateToMadSnapshot = BigDecimal.ONE;
	    this.totalMad = this.totalCurrency;
	    this.confirmedAt = confirmedAt;
	    this.status = PurchaseStatus.CONFIRMED;
	}
	public void cancelAfterConfirmation(LocalDateTime cancelledAt) {
	    if (status != PurchaseStatus.CONFIRMED) {
	        throw new IllegalStateException("Only confirmed purchases can be cancelled");
	    }

	    if (cancelledAt == null || cancelledAt.isBefore(confirmedAt)) {
	        throw new IllegalArgumentException(
	                "Cancellation time must be at or after confirmation");
	    }

	    this.cancelledAt = cancelledAt;
	    this.status = PurchaseStatus.CANCELLED;
	}
	public void confirmWithRate(
	        BigDecimal rateToMad,
	        BigDecimal convertedTotalMad,
	        String rateSource,
	        LocalDateTime rateRetrievedAt,
	        boolean rateFromCache,
	        LocalDateTime confirmedAt) {

	    if (status != PurchaseStatus.DRAFT) {
	        throw new IllegalStateException("Only draft purchases can be confirmed");
	    }

	    if (rateToMad == null || rateToMad.signum() <= 0) {
	        throw new IllegalArgumentException("Exchange rate to MAD must be positive");
	    }

	    if (rateToMad.stripTrailingZeros().scale() > 10) {
	        throw new IllegalArgumentException(
	                "Exchange rate to MAD cannot have more than 10 decimal places");
	    }

	    if ("MAD".equals(currency.getCode())
	            && rateToMad.compareTo(BigDecimal.ONE) != 0) {
	        throw new IllegalArgumentException("MAD purchases must use an exchange rate of 1");
	    }

	    if (convertedTotalMad == null || convertedTotalMad.signum() < 0) {
	        throw new IllegalArgumentException("Converted purchase total cannot be negative or missing");
	    }

	    if (confirmedAt == null) {
	        throw new IllegalArgumentException("Confirmation time is required");
	    }
	    if (rateSource == null || rateSource.isBlank()) {
	        throw new IllegalArgumentException(
	                "Rate source is required");
	    }

	    if (rateRetrievedAt == null) {
	        throw new IllegalArgumentException(
	                "Rate retrieval time is required");
	    }

	    this.rateToMadSnapshot = rateToMad;
	    this.rateSourceSnapshot = rateSource;
	    this.rateRetrievedAtSnapshot = rateRetrievedAt;
	    this.rateOverriddenSnapshot = false;
	    this.rateFromCacheSnapshot = rateFromCache;
	    this.totalMad = convertedTotalMad;
	    this.confirmedAt = confirmedAt;
	    this.status = PurchaseStatus.CONFIRMED;
	}
}





