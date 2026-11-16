package com.electrostock.backend.controller;

import com.electrostock.backend.dto.AddPurchaseItemRequest;
import com.electrostock.backend.dto.ConfirmPurchaseRequest;
import com.electrostock.backend.dto.CreatePurchaseRequest;
import com.electrostock.backend.model.Purchase;
import com.electrostock.backend.model.PurchaseItem;
import com.electrostock.backend.service.PurchaseService;
import com.electrostock.backend.dto.PurchaseResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    public record CreatePurchaseResponse(Long id) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePurchaseResponse createDraft(
            @Valid @RequestBody CreatePurchaseRequest request) {

        Purchase purchase = purchaseService.createDraft(
                request.getSupplierId(),
                request.getCurrencyCode(),
                request.getPurchaseDate());

        return new CreatePurchaseResponse(purchase.getId());
    }
    @PostMapping("/{purchaseId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Long addItem(
            @PathVariable("purchaseId") Long purchaseId,
            @Valid @RequestBody AddPurchaseItemRequest request) {
    	PurchaseItem purchaseItem = purchaseService.addItem(
    			purchaseId,
    			request.getProductId(),
    			request.getQuantity(),
    			request.getUnitPriceCurrency());
    	return  purchaseItem.getId();
    }
    @GetMapping("/{purchaseId}")
    public PurchaseResponse getPurchase(
            @PathVariable("purchaseId") Long purchaseId) {
        return purchaseService.getPurchase(purchaseId);
    }
    @PostMapping("/{purchaseId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPurchase(
            @PathVariable("purchaseId") Long purchaseId,
            @Valid @RequestBody ConfirmPurchaseRequest request) {

        purchaseService.confirmPurchaseInMad(
                purchaseId, request.getSerialsByItemId());
    }
}