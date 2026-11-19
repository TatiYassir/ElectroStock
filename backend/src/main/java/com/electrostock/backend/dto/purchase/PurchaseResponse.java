package com.electrostock.backend.dto.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.electrostock.backend.enums.PurchaseStatus;

public class PurchaseResponse {

    private final Long id;
    private final PurchaseStatus status;
    private final String currencyCode;
    private final BigDecimal totalCurrency;
    private final BigDecimal totalMad;
    private final BigDecimal rateToMadSnapshot;
    private final String rateSourceSnapshot;
    private final LocalDateTime rateRetrievedAtSnapshot;
    private final boolean rateFromCacheSnapshot;
    private final List<PurchaseItemResponse> items;
    private final String supplierName;
    private final LocalDate purchaseDate;
    private final LocalDateTime confirmedAt;
    private final LocalDateTime cancelledAt;

    public PurchaseResponse(
            Long id,
            PurchaseStatus status,
            String currencyCode,
            BigDecimal totalCurrency,
            BigDecimal totalMad,
            BigDecimal rateToMadSnapshot,
            String rateSourceSnapshot,
            LocalDateTime rateRetrievedAtSnapshot,
            boolean rateFromCacheSnapshot,
            List<PurchaseItemResponse> items,
            String supplierName, LocalDate purchaseDate,
            LocalDateTime confirmedAt, LocalDateTime cancelledAt) {

        this.id = id;
        this.status = status;
        this.currencyCode = currencyCode;
        this.totalCurrency = totalCurrency;
        this.totalMad = totalMad;
        this.rateToMadSnapshot = rateToMadSnapshot;
        this.rateSourceSnapshot = rateSourceSnapshot;
        this.rateRetrievedAtSnapshot = rateRetrievedAtSnapshot;
        this.rateFromCacheSnapshot = rateFromCacheSnapshot;
        this.items = items;
        this.supplierName = supplierName;
        this.purchaseDate = purchaseDate;
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;
    }

    public Long getId() {
        return id;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getTotalCurrency() {
        return totalCurrency;
    }

    public BigDecimal getTotalMad() {
        return totalMad;
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

    public boolean isRateFromCacheSnapshot() {
        return rateFromCacheSnapshot;
    }

    public List<PurchaseItemResponse> getItems() {
        return items;
    }
    public String getSupplierName() { return supplierName; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
}
