package com.electrostock.frontend.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Purchases HTTP calls. The backend remains responsible for saving and validating purchases. */
public final class PurchaseApiClient {
    public record Currency(String code, String name, String symbol) {
        @Override public String toString() { return code + " · " + name; }
    }

    public record Supplier(long id, String name, String status) {
        @Override public String toString() { return name; }
    }

    public record Summary(long id, String status, String supplierName, String currencyCode,
            LocalDate purchaseDate, BigDecimal totalCurrency) {}

    public record Line(long id, long productId, int quantity, BigDecimal unitPriceCurrency,
            BigDecimal lineTotalCurrency, String sku, String productName,
            boolean tracksSerials, List<String> serialNumbers) {}

    public record Purchase(long id, String status, String currencyCode, LocalDate purchaseDate,
            BigDecimal totalCurrency, BigDecimal totalMad, BigDecimal rateToMadSnapshot,
            String rateSourceSnapshot, LocalDateTime rateRetrievedAtSnapshot,
            boolean rateFromCacheSnapshot, List<Line> items, String supplierName,
            LocalDateTime confirmedAt, LocalDateTime cancelledAt) {}

    private record DraftRequest(long supplierId, String currencyCode, String purchaseDate) {}
    private record DraftId(long id) {}
    private record AddLineRequest(long productId, int quantity, BigDecimal unitPriceCurrency) {}
    private record ConfirmRequest(Map<Long, List<String>> serialsByItemId) {}
    private record ErrorBody(String message, String detail, String error) {}

    private final URI api;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public PurchaseApiClient() {
        this(URI.create("http://localhost:8080/api/"));
    }

    PurchaseApiClient(URI api) {
        this.api = api;
    }

    public CompletableFuture<List<Summary>> list() {
        return request("GET", "purchases", null, Summary[].class).thenApply(List::of);
    }

    public CompletableFuture<List<Currency>> currencies() {
        return request("GET", "currencies", null, Currency[].class).thenApply(List::of);
    }

    public CompletableFuture<List<Supplier>> suppliers() {
        return request("GET", "suppliers", null, Supplier[].class).thenApply(List::of);
    }

    public CompletableFuture<Purchase> get(long id) {
        return request("GET", "purchases/" + id, null, Purchase.class);
    }

    public CompletableFuture<Long> createDraft(long supplierId, String code, LocalDate date) {
        return request("POST", "purchases", new DraftRequest(supplierId, code, date.toString()),
                DraftId.class).thenApply(DraftId::id);
    }

    public CompletableFuture<Long> addLine(long purchaseId, long productId, int quantity, BigDecimal price) {
        return request("POST", "purchases/" + purchaseId + "/items",
                new AddLineRequest(productId, quantity, price), Long.class);
    }

    public CompletableFuture<Void> removeLine(long purchaseId, long lineId) {
        return request("DELETE", "purchases/" + purchaseId + "/items/" + lineId, null, Void.class);
    }

    public CompletableFuture<Void> confirm(long purchaseId, Map<Long, List<String>> serials) {
        return request("POST", "purchases/" + purchaseId + "/confirm", new ConfirmRequest(serials), Void.class);
    }

    public CompletableFuture<Void> cancel(long purchaseId) {
        return request("POST", "purchases/" + purchaseId + "/cancel", null, Void.class);
    }

    private <T> CompletableFuture<T> request(String method, String path, Object body, Class<T> type) {
        var builder = HttpRequest.newBuilder(api.resolve(path))
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
                    return type == Void.class ? null : json.readValue(response.body(), type);
                });
    }

    private String errorMessage(int status, String body) {
        String text = body.strip();
        if (text.startsWith("{")) {
            try {
                var problem = json.readValue(text, ErrorBody.class);
                for (String detail : new String[] {problem.message(), problem.detail(), problem.error()}) {
                    if (detail != null && !detail.isBlank()) return detail;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the status if the response isn't a normal API error.
            }
        } else if (!text.isEmpty() && !text.startsWith("<") && text.length() <= 300) {
            return text;
        }
        return "Purchases request failed (HTTP " + status + ").";
    }
}
