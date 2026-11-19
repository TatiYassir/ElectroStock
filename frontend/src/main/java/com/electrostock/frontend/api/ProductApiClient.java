package com.electrostock.frontend.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.json.JsonMapper;

public class ProductApiClient {
    public record Category(long id, String name, String description) {
        @Override public String toString() { return name; }
    }
    public record Product(long id, Category category, String sku, String name,
            boolean tracksSerials, String description, BigDecimal sellingPriceMad,
            BigDecimal averageCostMad, String status, int warrantyMonths, int lowStockThreshold) {}

    public record Edit(String sku, String name, String description, BigDecimal sellingPriceMad) {}
    public record Create(long categoryId, String sku, String name, boolean tracksSerials,
            String description, BigDecimal sellingPriceMad, int warrantyMonths, int lowStockThreshold) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public CompletableFuture<List<Product>> products() {
        return request("GET", "products", null, Product[].class).thenApply(List::of);
    }
    public CompletableFuture<List<Category>> categories() {
        return request("GET", "categories", null, Category[].class).thenApply(List::of);
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

    private <T> CompletableFuture<T> request(String method, String path, Object body, Class<T> type) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:8080/api/" + path))
                .timeout(Duration.ofSeconds(12)).header("Accept", "application/json");
        var payload = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        }
        return http.sendAsync(builder.method(method, payload).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        String message = response.body().strip();
                        if (message.startsWith("{")) {
                            message = "";
                        }
                        throw new IllegalStateException("HTTP " + response.statusCode()
                                + (message.isBlank() ? "" : " — " + message));
                    }
                    return json.readValue(response.body(), type);
                });
    }
}
