package com.electrostock.frontend.api;

import com.electrostock.frontend.api.InventoryReportClient.StockMovement;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.json.JsonMapper;

/** Read-only client for the reports and serial trace endpoints. */
public final class ReportsApiClient {
    private static final URI API = URI.create("http://localhost:8080/api/");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public record SupplierPurchases(long supplierId, String supplierName, long confirmedPurchases,
            BigDecimal totalPurchasedMad) {}

    public record ProductSales(long productId, String sku, String productName, long unitsSold,
            BigDecimal revenueMad) {}

    public record CategorySales(long categoryId, String categoryName, long unitsSold, BigDecimal revenueMad) {}

    public record ProductProfit(long productId, String sku, String productName, BigDecimal revenueMad,
            BigDecimal costMad, BigDecimal grossProfitMad) {}

    public record Movement(long id, long productId, String sku, String productName, String movementType,
            int quantityChange, BigDecimal valueChangeMad, LocalDateTime movementAt, String serialNumber,
            String adjustmentReason, String explanation, Long reversalOfMovementId) {}

    public record SerialTrace(long unitId, long productId, String sku, String productName, String serialNumber,
            String status, BigDecimal acquisitionCostMad, LocalDate warrantyStart, LocalDate warrantyEnd,
            List<StockMovement> movements) {}

    public CompletableFuture<List<SupplierPurchases>> purchasesBySupplier() {
        return report("purchases-by-supplier", SupplierPurchases[].class);
    }

    public CompletableFuture<List<ProductSales>> salesByProduct() {
        return report("sales-by-product", ProductSales[].class);
    }

    public CompletableFuture<List<CategorySales>> salesByCategory() {
        return report("sales-by-category", CategorySales[].class);
    }

    public CompletableFuture<List<ProductProfit>> profitByProduct() {
        return report("gross-profit-by-product", ProductProfit[].class);
    }

    public CompletableFuture<List<Movement>> movements(LocalDate from, LocalDate to) {
        return report("stock-movements?from=" + from + "&to=" + to, Movement[].class);
    }

    public CompletableFuture<Optional<SerialTrace>> traceSerial(String serial) {
        String encoded = URLEncoder.encode(serial, StandardCharsets.UTF_8);
        return get("products/units/trace?serialNumber=" + encoded).thenApply(response -> {
            if (response.statusCode() == 404) return Optional.empty();
            return Optional.of(read(response, SerialTrace.class));
        });
    }

    private <T> CompletableFuture<List<T>> report(String path, Class<T[]> type) {
        return get("reports/" + path).thenApply(response -> List.of(read(response, type)));
    }

    private CompletableFuture<HttpResponse<String>> get(String path) {
        var request = HttpRequest.newBuilder(API.resolve(path)).GET()
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json").build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private <T> T read(HttpResponse<String> response, Class<T> type) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return json.readValue(response.body(), type);
    }
}
