package com.electrostock.backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.electrostock.backend.enums.SaleStatus;

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
	//methods:
	public SaleItem addItem(Product product, int quantity, BigDecimal unitPriceCurrency) {
	    if (status != SaleStatus.DRAFT) {
	        throw new IllegalStateException("Only draft sales can be edited");
	    }

	    SaleItem item = new SaleItem(this, product, quantity, unitPriceCurrency);
	    totalCurrency = totalCurrency.add(item.getLineTotalCurrency());

	    return item;
	}
}
