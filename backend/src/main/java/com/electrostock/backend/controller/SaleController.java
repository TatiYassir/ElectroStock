package com.electrostock.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.electrostock.backend.dto.sale.AddSaleItemRequest;
import com.electrostock.backend.dto.sale.ConfirmSaleRequest;
import com.electrostock.backend.dto.sale.CreateSaleRequest;
import com.electrostock.backend.dto.sale.SaleResponse;
import com.electrostock.backend.dto.sale.SaleSummaryResponse;
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

    @GetMapping
    public List<SaleSummaryResponse> listSales() {
        return saleService.listSales();
    }

    @GetMapping("/products/{productId}/available-serials")
    public List<String> availableSerials(@PathVariable("productId") Long productId) {
        return saleService.availableSerials(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createDraft(
            @Valid @RequestBody CreateSaleRequest request) {

        Sale sale = saleService.createDraft(
                request.currencyCode(),
                request.saleDate());

        return Map.of("id", sale.getId());
    }

    @DeleteMapping("/{saleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable("saleId") Long saleId) {
        saleService.deleteDraft(saleId);
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

    @PutMapping("/{saleId}/items/{itemId}")
    public Long updateDraftItem(
            @PathVariable("saleId") Long saleId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody AddSaleItemRequest request) {

        SaleItem item = saleService.replaceDraftItem(
                saleId,
                itemId,
                request.quantity(),
                request.unitPriceCurrency());

        return item.getId();
    }

    @DeleteMapping("/{saleId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDraftItem(@PathVariable("saleId") Long saleId,
                                @PathVariable("itemId") Long itemId) {
        saleService.removeDraftItem(saleId, itemId);
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

    @PostMapping("/{saleId}/handover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handOverSale(@PathVariable("saleId") Long saleId) {
        saleService.handOverSale(saleId);
    }

    @PostMapping("/{saleId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSale(@PathVariable("saleId") Long saleId) {
        saleService.cancelSale(saleId);
    }
}