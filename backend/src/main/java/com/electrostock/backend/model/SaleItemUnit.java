package com.electrostock.backend.model;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sale_item_units", uniqueConstraints = @UniqueConstraint(
        name = "uq_sale_item_unit",
        columnNames = {"sale_item_id", "product_unit_id"}))
public class SaleItemUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_unit_id", nullable = false)
    private ProductUnit productUnit;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    protected SaleItemUnit() {
        // For JPA
    }

    public SaleItemUnit(SaleItem saleItem, ProductUnit productUnit,
                        LocalDateTime assignedAt) {
        if (saleItem == null || productUnit == null || assignedAt == null) {
            throw new IllegalArgumentException(
                    "Sale item, product unit, and assignment time are required");
        }

        Long productId = saleItem.getProduct().getId();
        if (productId == null
                || !Objects.equals(productId, productUnit.getProduct().getId())) {
            throw new IllegalArgumentException(
                    "Product unit must belong to the product on the sale line");
        }

        this.saleItem = saleItem;
        this.productUnit = productUnit;
        this.assignedAt = assignedAt;
    }

    public Long getId() {
        return id;
    }

    public SaleItem getSaleItem() {
        return saleItem;
    }

    public ProductUnit getProductUnit() {
        return productUnit;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }
}