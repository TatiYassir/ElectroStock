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

/** Sales HTTP calls. The backend remains responsible for saving and validating sales. */
public final class SaleApiClient {
    public record Currency(String code, String name, String symbol) {
        @Override public String toString() { return code + " · " + name; }
    }

    public record Summary(long id, String status, String currencyCode, LocalDate saleDate,
            BigDecimal totalCurrency, LocalDateTime handedOverAt) {}

    public record Line(long id, long productId, int quantity, BigDecimal unitPriceCurrency,
            BigDecimal lineTotalCurrency, String sku, String productName,
            boolean tracksSerials, List<String> serialNumbers) {}

    public record Sale(long id, String status, String currencyCode, LocalDate saleDate,
            BigDecimal totalCurrency, BigDecimal totalMad, BigDecimal rateToMadSnapshot,
            String rateSourceSnapshot, LocalDateTime rateRetrievedAtSnapshot,
            boolean rateFromCacheSnapshot, LocalDateTime handedOverAt, List<Line> items) {}

    private record DraftRequest(String currencyCode, String saleDate) {}
    private record DraftId(long id) {}
    private record AddLineRequest(long productId, int quantity, BigDecimal unitPriceCurrency) {}
    private record ConfirmRequest(Map<Long, List<String>> serialsByItemId) {}
    private record ErrorBody(String message, String detail, String error) {}

    private final URI api;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public SaleApiClient() {
        this(URI.create("http://localhost:8080/api/"));
    }

    SaleApiClient(URI api) {
        this.api = api;
    }

    public CompletableFuture<List<Summary>> list() {
        return request("GET", "sales", null, Summary[].class).thenApply(List::of);
    }

    public CompletableFuture<List<Currency>> currencies() {
        return request("GET", "currencies", null, Currency[].class).thenApply(List::of);
    }

    public CompletableFuture<Sale> get(long id) {
        return request("GET", "sales/" + id, null, Sale.class);
    }

    public CompletableFuture<Long> createDraft(String code, LocalDate date) {
        return request("POST", "sales", new DraftRequest(code, date.toString()),
                DraftId.class).thenApply(DraftId::id);
    }

    public CompletableFuture<Void> deleteDraft(long saleId) {
        return request("DELETE", "sales/" + saleId, null, Void.class);
    }

    public CompletableFuture<Long> addLine(long saleId, long productId, int quantity, BigDecimal price) {
        return request("POST", "sales/" + saleId + "/items",
                new AddLineRequest(productId, quantity, price), Long.class);
    }

    public CompletableFuture<Long> updateLine(long saleId, long lineId, long productId, int quantity, BigDecimal price) {
        return request("PUT", "sales/" + saleId + "/items/" + lineId,
                new AddLineRequest(productId, quantity, price), Long.class);
    }

    public CompletableFuture<Void> removeLine(long saleId, long lineId) {
        return request("DELETE", "sales/" + saleId + "/items/" + lineId, null, Void.class);
    }

    public CompletableFuture<List<String>> availableSerials(long productId) {
        return request("GET", "sales/products/" + productId + "/available-serials",
                null, String[].class).thenApply(List::of);
    }

    public CompletableFuture<Void> confirm(long saleId, Map<Long, List<String>> serials) {
        return request("POST", "sales/" + saleId + "/confirm", new ConfirmRequest(serials), Void.class);
    }

    public CompletableFuture<Void> handOver(long saleId) {
        return request("POST", "sales/" + saleId + "/handover", null, Void.class);
    }

    public CompletableFuture<Void> cancel(long saleId) {
        return request("POST", "sales/" + saleId + "/cancel", null, Void.class);
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
        return "Sales request failed (HTTP " + status + ").";
    }
}