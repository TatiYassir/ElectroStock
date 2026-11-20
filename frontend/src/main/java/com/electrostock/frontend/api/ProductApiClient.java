package com.electrostock.frontend.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.json.JsonMapper;

public class ProductApiClient {
    public record Category(long id, String name, String description) {
        @Override public String toString() { return name; }
    }
    public record Product(long id, Category category, String sku, String name,
            boolean tracksSerials, String description, BigDecimal sellingPriceMad,
            BigDecimal averageCostMad, String status, int warrantyMonths, int lowStockThreshold) {}

    public record CategoryRequest(String name,
            @JsonInclude(JsonInclude.Include.NON_NULL) String description) {}

    public record Edit(String sku, String name, String description, BigDecimal sellingPriceMad) {}
    public record Create(long categoryId, String sku, String name, boolean tracksSerials,
            String description, BigDecimal sellingPriceMad, int warrantyMonths, int lowStockThreshold) {}

    public enum AdjustmentReason { DAMAGED, MISSING, COUNTING_CORRECTION, OTHER }

    public record StockAdjustmentRequest(int quantityChange,
            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal unitCostMad,
            AdjustmentReason reason, String explanation) {}

    public record TrackedStockRemovalRequest(String serialNumber, AdjustmentReason reason, String explanation) {}

    public record TrackedStockAdditionRequest(String serialNumber, BigDecimal unitCostMad,
            AdjustmentReason reason, String explanation) {}

    public record TrackedStockRecoveryRequest(String serialNumber, String explanation) {}

    public record ProductUnitStatusResponse(String serialNumber, String status) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public CompletableFuture<List<Product>> products() {
        return request("GET", "products", null, Product[].class).thenApply(List::of);
    }
    public CompletableFuture<List<Category>> categories() {
        return request("GET", "categories", null, Category[].class).thenApply(List::of);
    }
    public CompletableFuture<Category> createCategory(String name, String description) {
        return request("POST", "categories", new CategoryRequest(name, description), Category.class);
    }
    public CompletableFuture<Category> updateCategory(long id, String name, String description) {
        return request("PUT", "categories/" + id, new CategoryRequest(name, description), Category.class);
    }
    public CompletableFuture<Void> deleteCategory(long id) {
        return request("DELETE", "categories/" + id, null, Void.class);
    }
    public CompletableFuture<Product> create(Create data) {
        return request("POST", "products", data, Product.class);
    }
    public CompletableFuture<Product> edit(long id, Edit data) {
        return request("PUT", "products/" + id, data, Product.class);
    }
    public CompletableFuture<Product> changeStatus(Product product) {
        String action = "ACTIVE".equals(product.status()) ? "deactivate" : "reactivate";
        return request("PATCH", "products/" + product.id() + "/" + action, null, Product.class);
    }

    public CompletableFuture<Void> adjustStock(long id, StockAdjustmentRequest data) {
        return request("POST", adjustmentPath(id), data, Void.class);
    }

    public CompletableFuture<Void> removeTrackedUnit(long id, TrackedStockRemovalRequest data) {
        return request("POST", adjustmentPath(id) + "/tracked-removal", data, Void.class);
    }

    public CompletableFuture<Void> addTrackedUnit(long id, TrackedStockAdditionRequest data) {
        return request("POST", adjustmentPath(id) + "/tracked-addition", data, Void.class);
    }

    public CompletableFuture<Void> recoverTrackedUnit(long id, TrackedStockRecoveryRequest data) {
        return request("POST", adjustmentPath(id) + "/tracked-recovery", data, Void.class);
    }

    public CompletableFuture<Optional<ProductUnitStatusResponse>> unitStatus(long id, String serialNumber) {
        String encoded = URLEncoder.encode(serialNumber, StandardCharsets.UTF_8);
        return request("GET", "products/" + id + "/units/status?serialNumber=" + encoded,
                null, ProductUnitStatusResponse.class, true).thenApply(Optional::ofNullable);
    }

    private static String adjustmentPath(long id) {
        return "products/" + id + "/stock-adjustments";
    }

    private <T> CompletableFuture<T> request(String method, String path, Object body, Class<T> type) {
        return request(method, path, body, type, false);
    }

    private <T> CompletableFuture<T> request(String method, String path, Object body, Class<T> type,
            boolean allowNotFound) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:8080/api/" + path))
                .timeout(Duration.ofSeconds(12)).header("Accept", "application/json, text/plain");
        var payload = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        }
        return http.sendAsync(builder.method(method, payload).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (allowNotFound && response.statusCode() == 404) return null;
                    if (response.statusCode() / 100 != 2) {
                        String message = response.body().strip();
                        if (message.startsWith("{")) {
                            message = "";
                        }
                        throw new IllegalStateException("HTTP " + response.statusCode()
                                + (message.isBlank() ? "" : " — " + message));
                    }
                    if (response.statusCode() == 204 || type == Void.class) return null;
                    return json.readValue(response.body(), type);
                });
    }
}