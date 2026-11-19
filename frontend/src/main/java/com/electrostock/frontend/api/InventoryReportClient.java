package com.electrostock.frontend.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.json.JsonMapper;

public class InventoryReportClient {

    private static final URI API_URI = URI.create("http://localhost:8080/api/");
    private static final URI REPORTS_URI = API_URI.resolve("reports/");

    public record LowStockItem(
            long productId,
            String sku,
            String productName,
            long stockQuantity,
            int lowStockThreshold) {}

    public record StockActivity(
            String movementType,
            String sku,
            String productName,
            int quantityChange,
            LocalDateTime movementAt) {}

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final JsonMapper mapper = JsonMapper.builder().build();

    public CompletableFuture<List<LowStockItem>> fetchLowStock() {
        return fetchRows("low-stock", LowStockItem[].class);
    }

    public CompletableFuture<List<StockActivity>> fetchRecentActivity() {
        return fetchRows("stock-movements/recent", StockActivity[].class);
    }

    public record StockMovement(
            long id,
            String movementType,
            int quantityChange,
            BigDecimal valueChangeMad,
            LocalDateTime movementAt,
            String serialNumber,
            String adjustmentReason,
            String explanation,
            Long reversalOfMovementId) {}

    public CompletableFuture<List<StockMovement>> fetchStockMovements(long productId) {
        URI endpoint = API_URI.resolve("products/" + productId + "/stock-movements");
        return fetchRows(endpoint, StockMovement[].class);
    }

    private <T> CompletableFuture<List<T>> fetchRows(String path, Class<T[]> rowType) {
        return fetchRows(REPORTS_URI.resolve(path), rowType);
    }

    private <T> CompletableFuture<List<T>> fetchRows(URI endpoint, Class<T[]> rowType) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException(
                                "GET " + endpoint.getPath() + " returned HTTP " + response.statusCode());
                    }

                    T[] rows = mapper.readValue(response.body(), rowType);
                    return List.of(rows);
                });
    }
    public record StockItem(
            long productId,
            String sku,
            String productName,
            long stockQuantity,
            BigDecimal stockValueMad,
            BigDecimal sellingPriceMad) {

        public BigDecimal potentialSalesMad() {
            return sellingPriceMad.multiply(BigDecimal.valueOf(stockQuantity));
        }

        public BigDecimal potentialProfitMad() {
            return potentialSalesMad().subtract(stockValueMad);
        }
    }

    public CompletableFuture<List<StockItem>> fetchInventoryValuation() {
        return fetchRows("inventory-valuation", StockItem[].class);
    }
    public CompletableFuture<List<LowStockItem>> fetchStockAvailability() {
        return fetchRows("stock-availability", LowStockItem[].class);
    }
    public record SalesDay(
            LocalDate date,
            BigDecimal revenueMad,
            BigDecimal profitMad,
            long unitsSold) {}

    public CompletableFuture<List<SalesDay>> fetchDailySales(LocalDate from, LocalDate to) {
        return fetchRows("daily-sales?from=" + from + "&to=" + to, SalesDay[].class);
    }
}
