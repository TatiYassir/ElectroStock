package com.electrostock.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.electrostock.backend.dto.sale.SaleItemResponse;
import com.electrostock.backend.dto.sale.SaleResponse;
import com.electrostock.backend.dto.sale.SaleSummaryResponse;
import com.electrostock.backend.enums.MovementType;
import com.electrostock.backend.enums.SaleStatus;
import com.electrostock.backend.enums.UnitStatus;
import com.electrostock.backend.exception.product.ProductNotFoundException;
import com.electrostock.backend.model.Currency;
import com.electrostock.backend.model.Product;
import com.electrostock.backend.model.ProductUnit;
import com.electrostock.backend.model.Sale;
import com.electrostock.backend.model.SaleItem;
import com.electrostock.backend.model.SaleItemUnit;
import com.electrostock.backend.model.StockMovement;
import com.electrostock.backend.repository.CurrencyRepository;
import com.electrostock.backend.repository.ProductRepository;
import com.electrostock.backend.repository.ProductUnitRepository;
import com.electrostock.backend.repository.SaleItemRepository;
import com.electrostock.backend.repository.SaleItemUnitRepository;
import com.electrostock.backend.repository.SaleRepository;
import com.electrostock.backend.repository.StockMovementRepository;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductRepository productRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductUnitRepository productUnitRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CostingService costingService;
    private final SaleItemUnitRepository saleItemUnitRepository;
    private final ExchangeRateService exchangeRateService;

    public SaleService(SaleRepository saleRepository,
            CurrencyRepository currencyRepository,
            ProductRepository productRepository,
            SaleItemRepository saleItemRepository,
            ProductUnitRepository productUnitRepository,
            StockMovementRepository stockMovementRepository,
            CostingService costingService,
            SaleItemUnitRepository saleItemUnitRepository,
            ExchangeRateService exchangeRateService) {
        this.saleRepository = saleRepository;
        this.currencyRepository = currencyRepository;
        this.productRepository = productRepository;
        this.saleItemRepository = saleItemRepository;
        this.productUnitRepository = productUnitRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.costingService = costingService;
        this.saleItemUnitRepository = saleItemUnitRepository;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional
    public Sale createDraft(String currencyCode, LocalDate saleDate) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code is required");
        }
        String normalizedCode = currencyCode.strip().toUpperCase(Locale.ROOT);
        Currency currency = currencyRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + normalizedCode));

        return saleRepository.save(new Sale(currency, saleDate));
    }

    @Transactional
    public SaleItem addItem(Long saleId, Long productId,
                            int quantity, BigDecimal unitPriceCurrency) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        long alreadyInDraft = saleItemRepository.findBySale_Id(saleId).stream()
                .filter(existing -> existing.getProduct().getId().equals(productId))
                .mapToLong(SaleItem::getQuantity)
                .sum();
        ensureProductStockAvailable(product, alreadyInDraft + quantity);

        SaleItem item = sale.addItem(product, quantity, unitPriceCurrency);
        return saleItemRepository.save(item);
    }

    @Transactional
    public SaleItem replaceDraftItem(Long saleId, Long itemId,
                                     int quantity, BigDecimal unitPriceCurrency) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));

        SaleItem existing = saleItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Sale item not found: " + itemId));

        Product product = existing.getProduct();
        long otherLinesQty = saleItemRepository.findBySale_Id(saleId).stream()
                .filter(line -> !line.getId().equals(itemId))
                .filter(line -> line.getProduct().getId().equals(product.getId()))
                .mapToLong(SaleItem::getQuantity)
                .sum();
        ensureProductStockAvailable(product, otherLinesQty + quantity);

        sale.removeDraftItem(existing);
        saleItemRepository.delete(existing);
        saleItemRepository.flush();

        SaleItem updated = sale.addItem(product, quantity, unitPriceCurrency);
        return saleItemRepository.save(updated);
    }

    @Transactional
    public void removeDraftItem(Long saleId, Long itemId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        SaleItem item = saleItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Sale item not found: " + itemId));
        sale.removeDraftItem(item);
        saleItemRepository.delete(item);
    }

    @Transactional
    public void deleteDraft(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        sale.validateDeletableDraft();

        List<SaleItem> items = saleItemRepository.findBySale_Id(saleId);
        if (!items.isEmpty()) {
            saleItemRepository.deleteAll(items);
        }
        saleRepository.delete(sale);
    }

    @Transactional(readOnly = true)
    public List<SaleSummaryResponse> listSales() {
        return saleRepository.findSaleSummaries();
    }

    @Transactional(readOnly = true)
    public List<String> availableSerials(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        if (!product.isTracksSerials()) {
            return List.of();
        }

        return productUnitRepository
                .findByProduct_IdAndStatusOrderBySerialNumberAsc(productId, UnitStatus.IN_STOCK)
                .stream()
                .map(ProductUnit::getSerialNumber)
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleResponse getSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));

        List<SaleItemResponse> itemResponses = new ArrayList<>();
        Map<Long, List<String>> itemSerials = new HashMap<>();
        for (SaleItemUnit link : saleItemUnitRepository.findBySaleItem_Sale_Id(saleId)) {
            itemSerials.computeIfAbsent(link.getSaleItem().getId(), key -> new ArrayList<>())
                    .add(link.getProductUnit().getSerialNumber());
        }

        for (SaleItem item : saleItemRepository.findBySale_Id(saleId)) {
            Product product = item.getProduct();
            itemResponses.add(new SaleItemResponse(
                    item.getId(),
                    product.getId(),
                    item.getQuantity(),
                    item.getUnitPriceCurrency(),
                    item.getLineTotalCurrency(),
                    product.getSku(),
                    product.getName(),
                    product.isTracksSerials(),
                    itemSerials.getOrDefault(item.getId(), List.of()).stream().sorted().toList()));
        }

        return new SaleResponse(
                sale.getId(),
                sale.getStatus(),
                sale.getCurrency().getCode(),
                sale.getSaleDate(),
                sale.getTotalCurrency(),
                sale.getTotalMad(),
                sale.getRateToMadSnapshot(),
                sale.getRateSourceSnapshot(),
                sale.getRateRetrievedAtSnapshot(),
                sale.isRateFromCacheSnapshot(),
                sale.getHandedOverAt(),
                itemResponses);
    }

    private void ensureProductStockAvailable(Product product, long requestedTotal) {
        long available = stockMovementRepository.getStockQuantity(product.getId());
        if (requestedTotal > available) {
            throw new IllegalStateException(
                    "Not enough stock for " + product.getSku() + " (" + product.getName() + ")"
                            + ": requested " + requestedTotal + ", available " + available);
        }
    }

    private ProductUnit requireAvailableUnit(SaleItem item, String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("Serial number is required");
        }

        String cleanedSerial = serialNumber.strip();

        ProductUnit unit = productUnitRepository.findBySerialNumber(cleanedSerial)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Serial number not found: " + cleanedSerial));

        if (!item.getProduct().getId().equals(unit.getProduct().getId())) {
            throw new IllegalArgumentException(
                    "Serial number belongs to a different product: " + cleanedSerial);
        }

        if (unit.getStatus() != UnitStatus.IN_STOCK) {
            throw new IllegalStateException("Unit is not in stock: " + cleanedSerial);
        }

        return unit;
    }

    private Map<Long, List<ProductUnit>> validateSelectedUnits(
            List<SaleItem> items,
            Map<Long, List<String>> serialsByItemId) {

        if (serialsByItemId == null) {
            throw new IllegalArgumentException("Use an empty map when no serials are selected");
        }

        Set<Long> validItemIds = new HashSet<>();
        for (SaleItem item : items) {
            validItemIds.add(item.getId());
        }

        for (Long submittedItemId : serialsByItemId.keySet()) {
            if (!validItemIds.contains(submittedItemId)) {
                throw new IllegalArgumentException("Line " + submittedItemId + " does not belong to this sale");
            }
        }

        Map<Long, List<ProductUnit>> unitsByItemId = new HashMap<>();
        Set<Long> selectedUnitIds = new HashSet<>();

        for (SaleItem item : items) {
            List<String> serials = serialsByItemId.getOrDefault(item.getId(), List.of());

            if (serials == null) {
                throw new IllegalArgumentException("Use an empty list instead of null for line " + item.getId());
            }

            int expectedCount = item.getProduct().isTracksSerials() ? item.getQuantity() : 0;

            if (serials.size() != expectedCount) {
                throw new IllegalArgumentException(
                        "Expected " + expectedCount + " serial numbers for line "
                                + item.getId() + ", but got " + serials.size());
            }

            List<ProductUnit> units = new ArrayList<>();
            for (String serial : serials) {
                ProductUnit unit = requireAvailableUnit(item, serial);

                if (!selectedUnitIds.add(unit.getId())) {
                    throw new IllegalArgumentException(
                            "Unit selected more than once: " + unit.getSerialNumber());
                }

                units.add(unit);
            }

            unitsByItemId.put(item.getId(), units);
        }

        return unitsByItemId;
    }

    private void validateStockAvailability(List<SaleItem> items) {
        Map<Long, Long> requestedByProductId = new HashMap<>();
        Map<Long, Product> productsById = new HashMap<>();

        for (SaleItem item : items) {
            Product product = item.getProduct();
            Long productId = product.getId();
            productsById.putIfAbsent(productId, product);
            requestedByProductId.merge(productId, (long) item.getQuantity(), Long::sum);
        }

        for (Map.Entry<Long, Long> entry : requestedByProductId.entrySet()) {
            Long productId = entry.getKey();
            long requested = entry.getValue();
            long available = stockMovementRepository.getStockQuantity(productId);

            if (requested > available) {
                Product product = productsById.get(productId);
                throw new IllegalStateException(
                        "Not enough stock for " + product.getSku() + " (" + product.getName() + ")"
                                + ": requested " + requested + ", available " + available);
            }
        }
    }

    @Transactional(readOnly = true)
    public void validateSaleConfirmation(
            Long saleId, Map<Long, List<String>> serialsByItemId) {

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));

        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new IllegalStateException("Only draft sales can be confirmed");
        }

        List<SaleItem> items = saleItemRepository.findBySale_Id(saleId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Add at least one item before confirming");
        }

        validateSelectedUnits(items, serialsByItemId);
        validateStockAvailability(items);
    }

    @Transactional
    public void confirmSaleInMad(
            Long saleId,
            Map<Long, List<String>> serialsByItemId) {

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));

        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new IllegalStateException("Only draft sales can be confirmed");
        }

        List<SaleItem> items = saleItemRepository.findBySale_Id(saleId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Add at least one item before confirming");
        }

        Map<Long, List<ProductUnit>> unitsByItemId = validateSelectedUnits(items, serialsByItemId);
        validateStockAvailability(items);

        LocalDateTime confirmedAt = LocalDateTime.now();
        ExchangeRateQuote quote = exchangeRateService.getRateToMad(sale.getCurrency().getCode());

        BigDecimal rateToMad = quote.rateToMad();
        BigDecimal totalMad = BigDecimal.ZERO;

        for (SaleItem item : items) {
            Product product = item.getProduct();
            item.snapshotCategory();

            BigDecimal lineCost;

            if (product.isTracksSerials()) {
                lineCost = BigDecimal.ZERO;

                for (ProductUnit unit : unitsByItemId.get(item.getId())) {
                    BigDecimal unitCost = unit.getAcquisitionCostMad();
                    lineCost = lineCost.add(unitCost);
                    unit.markSold();

                    saleItemUnitRepository.save(new SaleItemUnit(item, unit, confirmedAt));
                    stockMovementRepository.save(
                            StockMovement.forSale(item, unit, 1, unitCost, confirmedAt));
                }
            } else {
                lineCost = costingService.calculateUntrackedSaleCostMad(
                        product.getId(),
                        item.getQuantity());

                stockMovementRepository.saveAndFlush(
                        StockMovement.forSale(item, null, item.getQuantity(), lineCost, confirmedAt));
            }

            item.snapshotAmountsInMad(rateToMad, lineCost);
            totalMad = totalMad.add(item.getLineTotalMad());
        }

        sale.confirmWithRate(
                rateToMad,
                totalMad,
                quote.source(),
                quote.retrievedAt(),
                quote.fromCache(),
                confirmedAt);
    }

    @Transactional
    public void handOverSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));

        LocalDateTime handedOverAt = LocalDateTime.now();
        sale.markHandedOver(handedOverAt);

        for (SaleItemUnit link : saleItemUnitRepository.findBySaleItem_Sale_Id(saleId)) {
            link.getProductUnit().startWarrantyAtHandover(handedOverAt.toLocalDate());
        }
    }

    @Transactional
    public void cancelSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));

        LocalDateTime cancelledAt = LocalDateTime.now();
        sale.cancelBeforeHandover(cancelledAt);

        List<StockMovement> originalMovements =
                stockMovementRepository.findBySaleItem_Sale_IdAndMovementType(
                        saleId, MovementType.SALE);

        if (originalMovements.isEmpty()) {
            throw new IllegalStateException("No stock movements found for sale " + saleId);
        }

        for (StockMovement original : originalMovements) {
            ProductUnit unit = original.getProductUnit();
            if (unit != null) {
                unit.returnToStockAfterSaleCancellation();
            }

            stockMovementRepository.save(StockMovement.reverseSale(original, cancelledAt));
        }
    }
}