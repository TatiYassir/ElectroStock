package com.electrostock.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.electrostock.backend.model.ExchangeRateCache;

public interface ExchangeRateCacheRepository
        extends JpaRepository<ExchangeRateCache, Long> {

    Optional<ExchangeRateCache>
    findByBaseCurrencyCodeAndQuoteCurrencyCode(
            String baseCurrencyCode,
            String quoteCurrencyCode);
}