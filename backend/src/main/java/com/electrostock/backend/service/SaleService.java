package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.exception.ProductNotFoundException;
import com.electrostock.backend.model.Currency;
import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.Sale;
import com.electrostock.backend.model.SaleItem;
import com.electrostock.backend.repository.CurrencyRepository;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.SaleItemRepository;
import com.electrostock.backend.repository.SaleRepository;
import com.electrostock.backend.dto.SaleItemResponse;
import com.electrostock.backend.dto.SaleResponse;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductRepository productRepository;
    private final SaleItemRepository saleItemRepository;

    public SaleService(SaleRepository saleRepository,
                       CurrencyRepository currencyRepository,
                       ProductRepository productRepository,
                       SaleItemRepository saleItemRepository) {
        this.saleRepository = saleRepository;
        this.currencyRepository = currencyRepository;
        this.productRepository = productRepository;
        this.saleItemRepository = saleItemRepository;
    }

    public Sale createDraft(String currencyCode, LocalDate saleDate) {
        Currency currency = currencyRepository.findByCode(currencyCode)
                .orElseThrow(() -> new IllegalArgumentException("Currency not found"));

        return saleRepository.save(new Sale(currency, saleDate));
    }

    @Transactional
    public SaleItem addItem(Long saleId, Long productId,
                            int quantity, BigDecimal unitPriceCurrency) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sale not found: " + saleId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found: " + productId));

        SaleItem item = sale.addItem(product, quantity, unitPriceCurrency);
        return saleItemRepository.save(item);
    }
    @Transactional(readOnly = true)
    public SaleResponse getSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sale not found: " + saleId));

        List<SaleItemResponse> itemResponses = new ArrayList<>();

        for (SaleItem item : saleItemRepository.findBySale_Id(saleId)) {
            itemResponses.add(new SaleItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getQuantity(),
                    item.getUnitPriceCurrency(),
                    item.getLineTotalCurrency()));
        }

        return new SaleResponse(
                sale.getId(),
                sale.getStatus(),
                sale.getCurrency().getCode(),
                sale.getSaleDate(),
                sale.getTotalCurrency(),
                itemResponses);
    }
}