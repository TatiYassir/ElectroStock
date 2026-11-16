package com.electrostock.backend.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sale_items")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_currency", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceCurrency;

    @Column(name = "line_total_currency", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotalCurrency;

    @Column(name = "line_total_mad", precision = 19, scale = 4)
    private BigDecimal lineTotalMad;

    @Column(name = "cost_of_goods_sold_mad", precision = 19, scale = 4)
    private BigDecimal costOfGoodsSoldMad;
    //NO-ARGUMENT CONSTRUCTOR for JPA
    protected SaleItem() {
        // For JPA
    }
    //ARGUMENT CONSTRUCTOR :
    public SaleItem(Sale sale, Product product,
                    int quantity, BigDecimal unitPriceCurrency) {
        if (sale == null) {
            throw new IllegalArgumentException("Sale is required");
        }

        if (product == null) {
            throw new IllegalArgumentException("Product is required");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        if (unitPriceCurrency == null || unitPriceCurrency.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit price is required and cannot be negative");
        }

        this.sale = sale;
        this.product = product;
        this.quantity = quantity;
        this.unitPriceCurrency = unitPriceCurrency;
        this.lineTotalCurrency =
                unitPriceCurrency.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
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

    public BigDecimal getLineTotalCurrency() {
        return lineTotalCurrency;
    }

    public BigDecimal getLineTotalMad() {
        return lineTotalMad;
    }

    public BigDecimal getCostOfGoodsSoldMad() {
        return costOfGoodsSoldMad;
    }
}