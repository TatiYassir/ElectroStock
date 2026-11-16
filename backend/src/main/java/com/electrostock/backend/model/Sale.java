package com.electrostock.backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.electrostock.backend.enums.SaleStatus;
import com.electrostock.backend.exception.SaleCancellationNotAllowedException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sales")
public class Sale {
	//Attributes :
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status = SaleStatus.DRAFT;

    @Column(name = "total_currency", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCurrency = BigDecimal.ZERO;
    @Column(name = "rate_to_mad_snapshot", precision = 20, scale = 10)
    private BigDecimal rateToMadSnapshot;

    @Column(name = "total_mad", precision = 19, scale = 4)
    private BigDecimal totalMad;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    //NO-ARGUMENT CONSTRUCTOR for JPA :
    protected Sale() {
    }
    //ARGUMENT CONSTRUCTOR :
    public Sale(Currency currency, LocalDate saleDate) {
        if (currency == null) {
            throw new IllegalArgumentException("Sale currency is required");
        }

        if (saleDate == null) {
            throw new IllegalArgumentException("Sale date is required");
        }

        this.currency = currency;
        this.saleDate = saleDate;
    }
    //Getters:
	public Long getId() {
		return id;
	}
	public Currency getCurrency() {
		return currency;
	}
	public LocalDate getSaleDate() {
		return saleDate;
	}
	public SaleStatus getStatus() {
		return status;
	}
	public BigDecimal getTotalCurrency() {
		return totalCurrency;
	}
	public LocalDateTime getHandedOverAt() {
	    return handedOverAt;
	}
	public LocalDateTime getCancelledAt() {
	    return cancelledAt;
	}
	//methods:
	public SaleItem addItem(Product product, int quantity, BigDecimal unitPriceCurrency) {
	    if (status != SaleStatus.DRAFT) {
	        throw new IllegalStateException("Only draft sales can be edited");
	    }

	    SaleItem item = new SaleItem(this, product, quantity, unitPriceCurrency);
	    totalCurrency = totalCurrency.add(item.getLineTotalCurrency());

	    return item;
	}
	public void confirmInMad(LocalDateTime confirmedAt) {
	    if (status != SaleStatus.DRAFT) {
	        throw new IllegalStateException("Only draft sales can be confirmed");
	    }

	    if (!"MAD".equals(currency.getCode())) {
	        throw new IllegalStateException("Only MAD sales can be confirmed for now");
	    }

	    if (confirmedAt == null) {
	        throw new IllegalArgumentException("Confirmation time is required");
	    }

	    this.rateToMadSnapshot = BigDecimal.ONE;
	    this.totalMad = this.totalCurrency;
	    this.confirmedAt = confirmedAt;
	    this.status = SaleStatus.CONFIRMED;
	}
	public void markHandedOver(LocalDateTime handedOverAt) {
	    if (status != SaleStatus.CONFIRMED) {
	        throw new IllegalStateException("Only confirmed sales can be handed over");
	    }
	    if (this.handedOverAt != null) {
	        throw new IllegalStateException("Sale has already been handed over");
	    }
	    if (handedOverAt == null || handedOverAt.isBefore(confirmedAt)) {
	        throw new IllegalArgumentException(
	                "Handover time must be at or after confirmation");
	    }

	    this.handedOverAt = handedOverAt;
	}
	public void cancelBeforeHandover(LocalDateTime cancelledAt) {
	    if (status != SaleStatus.CONFIRMED) {
	        throw new SaleCancellationNotAllowedException("Only confirmed sales can be cancelled");
	    }

	    if (handedOverAt != null) {
	        throw new SaleCancellationNotAllowedException("Cannot cancel a sale after handover");
	    }

	    if (cancelledAt == null || cancelledAt.isBefore(confirmedAt)) {
	        throw new IllegalArgumentException(
	                "Cancellation time must be at or after confirmation");
	    }

	    this.cancelledAt = cancelledAt;
	    this.status = SaleStatus.CANCELLED;
	}
}
