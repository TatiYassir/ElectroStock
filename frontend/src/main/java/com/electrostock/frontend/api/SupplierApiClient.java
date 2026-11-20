package com.electrostock.frontend.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Supplier directory and procurement summary calls to the Spring Boot API. */
public final class SupplierApiClient {
    public record Supplier(long id, String name, String phone, String email, String address, String status) {
        public boolean active() { return "ACTIVE".equals(status); }
    }

    public record SupplierInput(String name, String phone, String email, String address) {}

    public record SupplierSpend(long supplierId, String supplierName, long confirmedPurchases,
            BigDecimal totalPurchasedMad) {}

    public record SupplierPurchase(long id, String status, String supplierName, String currencyCode,
            LocalDate purchaseDate, BigDecimal totalCurrency) {}

    private record ErrorBody(String message, String detail, String error) {}

    private static final URI API = URI.create("http://localhost:8080/api/");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public CompletableFuture<List<Supplier>> list() {
        return request("GET", "suppliers", null, Supplier[].class).thenApply(List::of);
    }

    public CompletableFuture<Supplier> get(long id) {
        return request("GET", "suppliers/" + id, null, Supplier.class);
    }

    public CompletableFuture<Supplier> create(SupplierInput input) {
        return request("POST", "suppliers", input, Supplier.class);
    }

    public CompletableFuture<Supplier> update(long id, SupplierInput input) {
        return request("PUT", "suppliers/" + id, input, Supplier.class);
    }

    public CompletableFuture<Supplier> setActive(long id, boolean active) {
        return request("PATCH", "suppliers/" + id + (active ? "/reactivate" : "/deactivate"),
                null, Supplier.class);
    }

    /** Optional procurement spend aggregation per supplier (degrades gracefully if unavailable). */
    public CompletableFuture<List<SupplierSpend>> spendSummary() {
        return request("GET", "reports/purchases-by-supplier", null, SupplierSpend[].class)
                .thenApply(List::of)
                .exceptionally(ignored -> List.of());
    }

    /** Optional purchase order list used to display recent PO history per supplier. */
    public CompletableFuture<List<SupplierPurchase>> purchases() {
        return request("GET", "purchases", null, SupplierPurchase[].class)
                .thenApply(List::of)
                .exceptionally(ignored -> List.of());
    }

    private <T> CompletableFuture<T> request(String method, String path, Object body, Class<T> type) {
        var builder = HttpRequest.newBuilder(API.resolve(path))
                .timeout(Duration.ofSeconds(25)).header("Accept", "application/json");
        var payload = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        }
        return http.sendAsync(builder.method(method, payload).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new IllegalStateException(errorMessage(response.statusCode(), response.body()));
                    }
                    return json.readValue(response.body(), type);
                });
    }

    private String errorMessage(int status, String body) {
        String text = body == null ? "" : body.strip();
        if (text.startsWith("{")) {
            try {
                var problem = json.readValue(text, ErrorBody.class);
                for (String detail : new String[] {problem.message(), problem.detail(), problem.error()}) {
                    if (detail != null && !detail.isBlank()) return detail;
                }
            } catch (RuntimeException ignored) {
                // Fall through to status code if payload is not a standard error body.
            }
        } else if (!text.isEmpty() && !text.startsWith("<") && text.length() <= 300) {
            return text;
        }
        return "Supplier request failed (HTTP " + status + ").";
    }
}