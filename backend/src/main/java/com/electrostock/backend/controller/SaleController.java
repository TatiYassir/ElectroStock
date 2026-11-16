package com.electrostock.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.validation.Valid;
import com.electrostock.backend.dto.AddSaleItemRequest;
import com.electrostock.backend.dto.ConfirmSaleRequest;
import com.electrostock.backend.dto.SaleResponse;
import com.electrostock.backend.dto.CreateSaleRequest;
import com.electrostock.backend.model.Sale;
import com.electrostock.backend.model.SaleItem;
import com.electrostock.backend.service.SaleService;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createDraft(
            @RequestBody CreateSaleRequest request) {

        Sale sale = saleService.createDraft(
                request.currencyCode(),
                request.saleDate());

        return Map.of("id", sale.getId());
    }
    @PostMapping("/{saleId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Long addItem(
            @PathVariable("saleId") Long saleId,
            @Valid @RequestBody AddSaleItemRequest request) {

        SaleItem item = saleService.addItem(
                saleId,
                request.productId(),
                request.quantity(),
                request.unitPriceCurrency());

        return item.getId();
    }
    @GetMapping("/{saleId}")
    public SaleResponse getSale(@PathVariable("saleId") Long saleId) {
        return saleService.getSale(saleId);
    }
    @PostMapping("/{saleId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmSale(
            @PathVariable("saleId") Long saleId,
            @Valid @RequestBody ConfirmSaleRequest request) {

        saleService.confirmSaleInMad(saleId, request.serialsByItemId());
    }
}