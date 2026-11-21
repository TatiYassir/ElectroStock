import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills an EMPTY ElectroStock database with realistic demo data through the
 * REST API, so costs, stock movements and profit are produced by the real
 * business rules (nothing is inserted into the database directly).
 *
 * Usage (backend must be running, database freshly created from schema.sql):
 *
 *     java database/SeedDemoData.java
 *     java database/SeedDemoData.java http://localhost:8080
 *
 * Foreign-currency purchases and sales (EUR, USD) fetch a live rate from the
 * Frankfurter API when they are confirmed, so an internet connection is needed.
 * All dates fall in the current month so the dashboard charts are populated.
 */
public class SeedDemoData {

    private static String api = "http://localhost:8080/api";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate MONTH_START = TODAY.withDayOfMonth(1);

    private static final Map<String, Long> categories = new LinkedHashMap<>();
    private static final Map<String, Long> suppliers = new LinkedHashMap<>();
    private static final Map<String, Long> productIds = new LinkedHashMap<>();
    private static final Map<String, Boolean> tracked = new LinkedHashMap<>();
    private static int serialCounter = 0;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            api = args[0].replaceAll("/+$", "") + "/api";
        }
        System.out.println("Seeding ElectroStock demo data via " + api);
        requireEmptyDatabase();

        seedCategories();
        seedSuppliers();
        seedProducts();
        seedPurchases();
        seedSales();
        seedAdjustment();

        // Medina stays on file, but paused, so the Suppliers screen shows both states.
        send("PATCH", "/suppliers/" + suppliers.get("medina") + "/deactivate", null);

        System.out.println();
        System.out.println("Done. Open the app and press Refresh.");
    }

    // ------------------------------------------------------------------ data

    private static void seedCategories() throws Exception {
        for (String name : List.of("Laptops", "Displays", "Peripherals", "Power", "Connectivity", "Storage")) {
            String body = send("POST", "/categories", "{\"name\":" + q(name) + "}");
            categories.put(name, idOf(body));
        }
        log("categories", categories.size());
    }

    private static void seedSuppliers() throws Exception {
        addSupplier("atlas", "Atlas Circuit Supply", "+212 5 22 41 08 30",
                "orders@atlas-circuit.example", "Casablanca, Morocco");
        addSupplier("koutoubia", "Koutoubia Tech Wholesale", "+212 5 24 31 75 22",
                "procurement@koutoubia-tech.example", "Marrakech, Morocco");
        addSupplier("medina", "Medina Power and Accessories", "+212 5 37 70 15 42",
                "orders@medina-power.example", "Rabat, Morocco");
        addSupplier("rif", "Rif Desktop Distribution", "+212 5 39 33 27 64",
                "sales@rif-desktop.example", "Tangier, Morocco");
        log("suppliers", suppliers.size());
    }

    private static void addSupplier(String key, String name, String phone, String email, String address)
            throws Exception {
        String body = send("POST", "/suppliers", "{\"name\":" + q(name) + ",\"phone\":" + q(phone)
                + ",\"email\":" + q(email) + ",\"address\":" + q(address) + "}");
        suppliers.put(key, idOf(body));
    }

    private static void seedProducts() throws Exception {
        //         sku            name                                category       serial  price    warranty threshold
        addProduct("16IRX9",      "Lenovo Legion 5 Gen 9",            "Laptops",     true,  "18500",  24, 2);
        addProduct("82JK00FAFE",  "Lenovo Legion 5 15ITH6",           "Laptops",     true,  "14190",  24, 2);
        addProduct("LT-5005",     "AtlasBook 15 Ryzen 5 Laptop",      "Laptops",     true,  "6990",   12, 2);
        addProduct("DSP-2424",    "24-inch IPS Monitor",              "Displays",    false, "1199",   24, 3);
        addProduct("DSP-ARM",     "Single Monitor Desk Arm",          "Displays",    false, "289",    12, 3);
        addProduct("KB-MECH87",   "87-key Mechanical Keyboard",       "Peripherals", false, "349",    12, 4);
        addProduct("MS-WL24",     "2.4 GHz Wireless Mouse",           "Peripherals", false, "89",     6,  5);
        addProduct("AUD-HS35",    "G35 Gaming Headset",               "Peripherals", false, "449",    12, 3);
        addProduct("CBL-HDMI2",   "HDMI 2.0 Cable 2 m",               "Connectivity",false, "59",     0,  10);
        addProduct("USB-HUB7",    "USB-C 7-in-1 Hub",                 "Connectivity",false, "249",    12, 4);
        addProduct("USB-DOCK",    "Dual Display USB-C Dock",          "Connectivity",false, "899",    12, 2);
        addProduct("PWR-GAN65",   "65 W GaN USB-C Charger",           "Power",       false, "249",    12, 4);
        addProduct("PWR-USB30",   "30 W USB Charger",                 "Power",       false, "129",    12, 5);
        addProduct("SSD-1TNV",    "1 TB NVMe SSD",                    "Storage",     false, "899",    36, 3);
        log("products", productIds.size());
    }

    private static void addProduct(String sku, String name, String category, boolean serial,
            String price, int warrantyMonths, int threshold) throws Exception {
        String body = send("POST", "/products", "{\"categoryId\":" + categories.get(category)
                + ",\"sku\":" + q(sku) + ",\"name\":" + q(name)
                + ",\"tracksSerials\":" + serial
                + ",\"sellingPriceMad\":" + price
                + ",\"warrantyMonths\":" + warrantyMonths
                + ",\"lowStockThreshold\":" + threshold + "}");
        productIds.put(sku, idOf(body));
        tracked.put(sku, serial);
    }

    private static void seedPurchases() throws Exception {
        //          supplier     cur    day   outcome      lines: sku, qty, unit price in that currency
        purchase("atlas",     "MAD", 2,  "CONFIRM", l("CBL-HDMI2", 40, "22"), l("MS-WL24", 30, "45"),
                l("PWR-USB30", 25, "60"));
        purchase("rif",       "MAD", 3,  "CONFIRM", l("DSP-2424", 12, "790"), l("DSP-ARM", 15, "145"),
                l("KB-MECH87", 20, "205"));
        purchase("koutoubia", "EUR", 4,  "CONFIRM", l("16IRX9", 6, "1250"), l("82JK00FAFE", 5, "950"));
        purchase("medina",    "MAD", 6,  "CONFIRM", l("PWR-GAN65", 20, "135"), l("USB-HUB7", 15, "125"),
                l("USB-DOCK", 6, "560"));
        purchase("atlas",     "USD", 8,  "CONFIRM", l("AUD-HS35", 12, "28"), l("SSD-1TNV", 10, "55"));
        purchase("rif",       "MAD", 10, "CONFIRM", l("LT-5005", 5, "5300"));
        purchase("atlas",     "MAD", 14, "CANCEL",  l("MS-WL24", 10, "46"));
        purchase("koutoubia", "EUR", 20, "DRAFT",   l("16IRX9", 3, "1260"));
        log("purchases", 8);
    }

    private static void seedSales() throws Exception {
        sale("MAD", 4,  "HANDOVER", l("MS-WL24", 3, "89"), l("KB-MECH87", 2, "349"));
        sale("MAD", 5,  "HANDOVER", l("DSP-2424", 3, "1199"), l("DSP-ARM", 3, "289"));
        sale("MAD", 6,  "HANDOVER", l("CBL-HDMI2", 8, "59"), l("PWR-USB30", 5, "129"));
        sale("MAD", 7,  "HANDOVER", l("16IRX9", 1, "18500"));
        sale("MAD", 9,  "HANDOVER", l("PWR-GAN65", 6, "249"), l("USB-HUB7", 4, "249"));
        sale("USD", 10, "HANDOVER", l("AUD-HS35", 3, "55"));
        sale("MAD", 11, "HANDOVER", l("USB-DOCK", 6, "899"));
        sale("MAD", 12, "HANDOVER", l("82JK00FAFE", 1, "14190"));
        sale("MAD", 13, "HANDOVER", l("LT-5005", 2, "6990"));
        sale("USD", 14, "HANDOVER", l("SSD-1TNV", 2, "105"));
        sale("MAD", 15, "HANDOVER", l("16IRX9", 1, "18500"), l("MS-WL24", 10, "89"));
        sale("MAD", 17, "HANDOVER", l("DSP-2424", 4, "1199"), l("KB-MECH87", 5, "349"));
        sale("MAD", 18, "HANDOVER", l("PWR-GAN65", 14, "249"), l("AUD-HS35", 9, "449"));
        sale("MAD", 19, "HANDOVER", l("MS-WL24", 15, "89"));
        sale("MAD", 19, "CANCEL",   l("82JK00FAFE", 1, "14190"));
        sale("MAD", 20, "HANDOVER", l("CBL-HDMI2", 6, "59"));
        sale("MAD", 20, "AWAITING", l("16IRX9", 1, "18500"), l("LT-5005", 1, "6990"));
        sale("MAD", 21, "HANDOVER", l("KB-MECH87", 2, "349"), l("DSP-ARM", 2, "289"));
        sale("MAD", 21, "DRAFT",    l("DSP-2424", 1, "1199"));
        log("sales", 19);
    }

    private static void seedAdjustment() throws Exception {
        send("POST", "/products/" + productIds.get("CBL-HDMI2") + "/stock-adjustments",
                "{\"quantityChange\":-1,\"reason\":\"DAMAGED\"}");
        log("stock adjustments", 1);
    }

    // ------------------------------------------------------------- workflows

    /** outcome: CONFIRM, CANCEL (confirm then cancel) or DRAFT. */
    private static void purchase(String supplier, String currency, int day, String outcome, String[]... lines)
            throws Exception {
        String body = send("POST", "/purchases", "{\"supplierId\":" + suppliers.get(supplier)
                + ",\"currencyCode\":" + q(currency) + ",\"purchaseDate\":" + q(date(day).toString()) + "}");
        long purchaseId = idOf(body);

        Map<Long, List<String>> serials = new LinkedHashMap<>();
        for (String[] line : lines) {
            String sku = line[0];
            int qty = Integer.parseInt(line[1]);
            String itemBody = send("POST", "/purchases/" + purchaseId + "/items",
                    "{\"productId\":" + productIds.get(sku) + ",\"quantity\":" + qty
                            + ",\"unitPriceCurrency\":" + line[2] + "}");
            if (tracked.get(sku)) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < qty; i++) {
                    list.add(String.format("%s-%03d", sku.replaceAll("[^A-Za-z0-9]", ""), ++serialCounter));
                }
                serials.put(Long.parseLong(itemBody.trim()), list);
            }
        }
        if (outcome.equals("DRAFT")) return;

        send("POST", "/purchases/" + purchaseId + "/confirm", serialsJson(serials));
        if (outcome.equals("CANCEL")) {
            send("POST", "/purchases/" + purchaseId + "/cancel", null);
        }
    }

    /** outcome: HANDOVER, AWAITING (confirmed, not handed over), CANCEL or DRAFT. */
    private static void sale(String currency, int day, String outcome, String[]... lines) throws Exception {
        String body = send("POST", "/sales", "{\"currencyCode\":" + q(currency)
                + ",\"saleDate\":" + q(date(day).toString()) + "}");
        long saleId = idOf(body);

        Map<Long, List<String>> serials = new LinkedHashMap<>();
        for (String[] line : lines) {
            String sku = line[0];
            int qty = Integer.parseInt(line[1]);
            String itemBody = send("POST", "/sales/" + saleId + "/items",
                    "{\"productId\":" + productIds.get(sku) + ",\"quantity\":" + qty
                            + ",\"unitPriceCurrency\":" + line[2] + "}");
            if (tracked.get(sku)) {
                String available = send("GET", "/sales/products/" + productIds.get(sku) + "/available-serials", null);
                List<String> pick = new ArrayList<>();
                Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(available);
                while (m.find() && pick.size() < qty) {
                    pick.add(m.group(1));
                }
                if (pick.size() < qty) {
                    throw new IllegalStateException("Not enough serial units available for " + sku);
                }
                serials.put(Long.parseLong(itemBody.trim()), pick);
            }
        }
        if (outcome.equals("DRAFT")) return;

        send("POST", "/sales/" + saleId + "/confirm", serialsJson(serials));
        switch (outcome) {
            case "HANDOVER" -> send("POST", "/sales/" + saleId + "/handover", null);
            case "CANCEL" -> send("POST", "/sales/" + saleId + "/cancel", null);
            default -> { /* AWAITING: leave confirmed */ }
        }
    }

    // --------------------------------------------------------------- helpers

    private static void requireEmptyDatabase() throws Exception {
        for (String path : List.of("/products", "/suppliers", "/categories")) {
            String body = send("GET", path, null).trim();
            if (!body.equals("[]")) {
                throw new IllegalStateException("The database already contains data (" + path
                        + "). Recreate it from database/schema.sql first, then run this again.");
            }
        }
    }

    private static String[] l(String sku, int qty, String unitPrice) {
        return new String[] { sku, String.valueOf(qty), unitPrice };
    }

    /** Day of the current month, never later than today. */
    private static LocalDate date(int dayOfMonth) {
        LocalDate d = MONTH_START.plusDays(dayOfMonth - 1L);
        return d.isAfter(TODAY) ? TODAY : d;
    }

    private static String serialsJson(Map<Long, List<String>> serials) {
        StringBuilder sb = new StringBuilder("{\"serialsByItemId\":{");
        boolean first = true;
        for (Map.Entry<Long, List<String>> e : serials.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":[");
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(q(e.getValue().get(i)));
            }
            sb.append(']');
        }
        return sb.append("}}").toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Reads the top-level "id" only; nested objects (e.g. a product's category) are ignored. */
    private static long idOf(String json) {
        StringBuilder top = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (char c : json.toCharArray()) {
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                if (depth <= 1) top.append(c);
                continue;
            }
            if (c == '"') {
                inString = true;
                if (depth <= 1) top.append(c);
            } else if (c == '{' || c == '[') {
                depth++;
                if (depth <= 1) top.append(c);
            } else if (c == '}' || c == ']') {
                if (depth <= 1) top.append(c);
                depth--;
            } else if (depth <= 1) {
                top.append(c);
            }
        }
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(top);
        if (!m.find()) throw new IllegalStateException("No id in response: " + json);
        return Long.parseLong(m.group(1));
    }

    private static void log(String what, int count) {
        System.out.printf("  %-18s %d%n", what, count);
    }

    private static String send(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        b.method(method, json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json));
        HttpResponse<String> r;
        try {
            r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IllegalStateException("Cannot reach the backend at " + api + ". Start it first.");
        }
        if (r.statusCode() >= 300) {
            throw new IllegalStateException(method + " " + path + " -> " + r.statusCode() + ": " + r.body()
                    + (json == null ? "" : "\n  request: " + json));
        }
        return r.body();
    }
}