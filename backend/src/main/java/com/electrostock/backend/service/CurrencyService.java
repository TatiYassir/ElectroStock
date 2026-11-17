package com.electrostock.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.electrostock.backend.model.Currency;
import com.electrostock.backend.dto.currency.CurrencyResponse;
import com.electrostock.backend.repository.CurrencyRepository;

@Service
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final ExchangeRateService exchangeRateService;

    public CurrencyService(
            CurrencyRepository currencyRepository,
            ExchangeRateService exchangeRateService) {
        this.currencyRepository = currencyRepository;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> listCurrencies() {
        return currencyRepository.findAllByOrderByCodeAsc()
                .stream()
                .map(currency -> new CurrencyResponse(
                        currency.getCode(),
                        currency.getName(),
                        currency.getSymbol()))
                .toList();
    }
    @Transactional
    public CurrencyResponse createCurrency(String code, String name, String symbol) {
        if (currencyRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Currency already exists: " + code);
        }
        exchangeRateService.getRateToMad(code);
        Currency saved = currencyRepository.save(new Currency(code, name, symbol));

        return new CurrencyResponse(
                saved.getCode(),
                saved.getName(),
                saved.getSymbol());
    }
}