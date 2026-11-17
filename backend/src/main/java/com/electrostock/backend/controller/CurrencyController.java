package com.electrostock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.electrostock.backend.dto.currency.CreateCurrencyRequest;
import com.electrostock.backend.dto.currency.CurrencyResponse;
import com.electrostock.backend.service.CurrencyService;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping
    public List<CurrencyResponse> listCurrencies() {
        return currencyService.listCurrencies();
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CurrencyResponse createCurrency(
            @Valid @RequestBody CreateCurrencyRequest request) {
        return currencyService.createCurrency(
                request.code(),
                request.name(),
                request.symbol());
    }
}