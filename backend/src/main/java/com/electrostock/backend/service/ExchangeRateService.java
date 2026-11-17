package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.electrostock.backend.model.ExchangeRateCache;
import com.electrostock.backend.repository.ExchangeRateCacheRepository;

@Service
public class ExchangeRateService {

    private static final String BASE_CURRENCY = "MAD";
    private static final String PROVIDER_NAME = "Frankfurter";

    private final RestClient restClient;
    private final ExchangeRateCacheRepository cacheRepository;

    public ExchangeRateService(
            ExchangeRateCacheRepository cacheRepository) {

        this.restClient = RestClient.builder()
        		.baseUrl("https://api.frankfurter.dev")
                .build();

        this.cacheRepository = cacheRepository;
    }

    public ExchangeRateQuote getRateToMad(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency code is required");
        }

        String normalizedCode =
                currencyCode.strip().toUpperCase();

        if (BASE_CURRENCY.equals(normalizedCode)) {
            return new ExchangeRateQuote(
                    BigDecimal.ONE,
                    "System",
                    LocalDateTime.now(),
                    false);
        }

        try {
            BigDecimal onlineRate =
                    fetchOnlineRate(normalizedCode);

            LocalDateTime retrievedAt = LocalDateTime.now();

            saveRate(
                    normalizedCode,
                    onlineRate,
                    PROVIDER_NAME,
                    retrievedAt);

            return new ExchangeRateQuote(
                    onlineRate,
                    PROVIDER_NAME,
                    retrievedAt,
                    false);

        } catch (RuntimeException exception) {
            return getCachedRate(normalizedCode);
        }
    }

    private BigDecimal fetchOnlineRate(String currencyCode) {
        Map<String, Object> response = restClient.get()
                .uri("/v2/rate/{base}/MAD", currencyCode)
                .retrieve()
                .body(new ParameterizedTypeReference<
                        Map<String, Object>>() {
                });

        if (response == null || response.get("rate") == null) {
            throw new IllegalStateException(
                    "Exchange rate was not returned");
        }

        return new BigDecimal(
                response.get("rate").toString());
    }

    private void saveRate(
            String currencyCode,
            BigDecimal rate,
            String source,
            LocalDateTime retrievedAt) {

        ExchangeRateCache cachedRate =
                cacheRepository
                        .findByBaseCurrencyCodeAndQuoteCurrencyCode(
                                currencyCode,
                                BASE_CURRENCY)
                        .orElse(null);

        if (cachedRate == null) {
            cachedRate = new ExchangeRateCache(
                    currencyCode,
                    BASE_CURRENCY,
                    rate,
                    source,
                    retrievedAt);
        } else {
            cachedRate.updateRate(
                    rate,
                    source,
                    retrievedAt);
        }

        cacheRepository.save(cachedRate);
    }

    private ExchangeRateQuote getCachedRate(String currencyCode) {
        ExchangeRateCache cachedRate =
                cacheRepository
                        .findByBaseCurrencyCodeAndQuoteCurrencyCode(
                                currencyCode,
                                BASE_CURRENCY)
                        .orElseThrow(() -> new IllegalStateException(
                                "Exchange rate unavailable online and no "
                                        + "cached rate exists for "
                                        + currencyCode));

        return new ExchangeRateQuote(
                cachedRate.getRate(),
                cachedRate.getSource() + " cache",
                cachedRate.getRetrievedAt(),
                true);
    }
}