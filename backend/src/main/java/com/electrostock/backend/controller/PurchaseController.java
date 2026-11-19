package com.electrostock.backend.controller;

import java.util.List;

import com.electrostock.backend.dto.purchase.AddPurchaseItemRequest;
import com.electrostock.backend.dto.purchase.ConfirmPurchaseRequest;
import com.electrostock.backend.dto.purchase.CreatePurchaseRequest;
import com.electrostock.backend.dto.purchase.PurchaseResponse;
import com.electrostock.backend.dto.purchase.PurchaseSummaryResponse;
import com.electrostock.backend.model.Purchase;
import com.electrostock.backend.model.PurchaseItem;
import com.electrostock.backend.service.PurchaseService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    public record CreatePurchaseResponse(Long id) {}

    @GetMapping
    public List<PurchaseSummaryResponse> listPurchases() {
        return purchaseService.listPurchases();
    }

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

    @DeleteMapping("/{purchaseId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDraftItem(@PathVariable("purchaseId") Long purchaseId,
            @PathVariable("itemId") Long itemId) {
        purchaseService.removeDraftItem(purchaseId, itemId);
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
    @PostMapping("/{purchaseId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelPurchase(@PathVariable("purchaseId") Long purchaseId) {
        purchaseService.cancelPurchase(purchaseId);
    }
}
