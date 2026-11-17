package com.electrostock.backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exchange_rate_cache")
public class ExchangeRateCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_currency_code",
            nullable = false,
            length = 3)
    private String baseCurrencyCode;

    @Column(name = "quote_currency_code",
            nullable = false,
            length = 3)
    private String quoteCurrencyCode;

    @Column(name = "rate",
            nullable = false,
            precision = 20,
            scale = 10)
    private BigDecimal rate;

    @Column(name = "source",
            nullable = false,
            length = 100)
    private String source;

    @Column(name = "retrieved_at",
            nullable = false)
    private LocalDateTime retrievedAt;

    protected ExchangeRateCache() {
    }

    public ExchangeRateCache(
            String baseCurrencyCode,
            String quoteCurrencyCode,
            BigDecimal rate,
            String source,
            LocalDateTime retrievedAt) {

        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.rate = rate;
        this.source = source;
        this.retrievedAt = retrievedAt;
    }

    public Long getId() {
        return id;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getRetrievedAt() {
        return retrievedAt;
    }

    public void updateRate(
            BigDecimal rate,
            String source,
            LocalDateTime retrievedAt) {

        this.rate = rate;
        this.source = source;
        this.retrievedAt = retrievedAt;
    }
}