package com.electrostock.frontend.view;

import com.electrostock.frontend.api.InventoryReportClient;
import com.electrostock.frontend.api.InventoryReportClient.LowStockItem;
import com.electrostock.frontend.api.InventoryReportClient.SalesDay;
import com.electrostock.frontend.api.InventoryReportClient.StockItem;
import com.electrostock.frontend.api.InventoryReportClient.StockMovement;
import com.electrostock.frontend.api.ReportsApiClient;
import com.electrostock.frontend.api.ReportsApiClient.CategorySales;
import com.electrostock.frontend.api.ReportsApiClient.Movement;
import com.electrostock.frontend.api.ReportsApiClient.ProductProfit;
import com.electrostock.frontend.api.ReportsApiClient.ProductSales;
import com.electrostock.frontend.api.ReportsApiClient.SerialTrace;
import com.electrostock.frontend.api.ReportsApiClient.SupplierPurchases;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.converter.LocalDateStringConverter;

/** Read-only report browser. Fetches one report at a time using the existing backend. */
public final class ReportsView extends VBox {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yy, HH:mm", Locale.ENGLISH);

    private enum Scope { SNAPSHOT, ALL_TIME, DATES, SERIAL }
    private enum Accent { COBALT, EMERALD, AMBER, CRIMSON }
    private enum CellKind {
        PLAIN, STRONG, AMOUNT, SKU, MOVEMENT, DELTA_QTY, DELTA_MONEY, PROFIT, STOCK_ALERT
    }

    private record Query(LocalDate from, LocalDate to, String serial) {}
    private record Metric(String label, String value, Accent accent) {}
    private record Result<T>(List<T> rows, List<Metric> metrics, String summary, String emptyMessage,
            ReportCharts.Model chart) {}
    private record Column<T, V>(String heading, int width, boolean alignRight, CellKind kind,
            Function<T, V> value, Function<V, String> display) {}
    private record Report<T>(String name, String description, Scope scope, List<Column<T, ?>> columns,
            Function<Query, CompletableFuture<Result<T>>> load) {
        @Override public String toString() { return name; }
    }

    private final InventoryReportClient inventory = new InventoryReportClient();
    private final ReportsApiClient api = new ReportsApiClient();
    private final ComboBox<Report<?>> reportChoice = new ComboBox<>();
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final TextField serial = new TextField();
    private final Button run = new Button("Refresh");
    private final Button export = new Button("Export CSV");
    private final Label description = label("", "report-muted");
    private final Label scopeLabel = label("", "report-scope");
    private final Label resultCount = label("", "report-count-pill");
    private final Label resultsTitle = label("Results", "report-heading");
    private final HBox metrics = line();
    private final TextField rowSearch = new TextField();
    private final Button clearSearch = new Button("Clear");
    private final ToggleButton tableToggle = new ToggleButton("Table");
    private final ToggleButton chartToggle = new ToggleButton("Chart");
    private final HBox viewSwitch = new HBox(2, tableToggle, chartToggle);
    private final Label searchNote = label("", "report-muted");
    private Consumer<String> filterCurrent = ignored -> {};
    private Runnable displayCurrent = () -> {};
    private final Label summary = label("", "report-summary");
    private final Label notice = label("", "report-notice");
    private final HBox datePresets = new HBox(6);
    private final HBox range = line(labeled("From", from), labeled("To", to), labeled("Quick range", datePresets));
    private final VBox serialFilter = labeled("Serial number", serial);
    private final VBox resultsPane = new VBox(10);
    private long requestNumber;
    private Runnable exportCurrent;
    private boolean exporting;
    private boolean opened;

    public ReportsView() {
        getStyleClass().add("reports-page");
        var css = ReportsView.class.getResource("reports.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
        setMinWidth(0);

        reportChoice.setAccessibleText("Choose report");
        reportChoice.getItems().setAll(reports());
        reportChoice.setMaxWidth(Double.MAX_VALUE);

        from.setAccessibleText("From date");
        to.setAccessibleText("To date");
        DateTimeFormatter dateInput = DateTimeFormatter.ISO_LOCAL_DATE;
        for (DatePicker picker : List.of(from, to)) {
            picker.setConverter(new LocalDateStringConverter(dateInput, dateInput));
            picker.setPromptText("YYYY-MM-DD");
            picker.setPrefWidth(132);
        }

        setupDatePresets();

        serial.setPromptText("Enter exact serial number…");
        serial.setAccessibleText("Serial number");
        serial.setPrefWidth(255);
        serial.setOnAction(event -> load());

        run.getStyleClass().add("report-primary");
        run.setOnAction(event -> load());

        export.getStyleClass().add("report-secondary");
        export.setDisable(true);
        export.setTooltip(new Tooltip("Exports the rows matching the table search, in table order."));
        export.setOnAction(event -> {
            if (exportCurrent != null) exportCurrent.run();
        });

        VBox title = new VBox(3,
                label("Reports & Analytics", "report-title"),
                label("Monitor revenue, gross margins, supplier spend, valuation, and serial audit trails.", "report-muted"));
        HBox header = line(title, spacer(), export);

        VBox selection = labeled("Report", reportChoice);
        selection.setMinWidth(220);
        selection.setPrefWidth(270);
        HBox.setHgrow(selection, Priority.ALWAYS);

        HBox filters = line(selection, range, serialFilter, run);
        filters.setAlignment(Pos.BOTTOM_LEFT);

        description.setWrapText(true);
        HBox context = line(description, spacer(), scopeLabel);
        VBox controls = card(filters, context);

        for (Label value : List.of(summary, notice)) {
            value.setWrapText(true);
            value.visibleProperty().bind(value.textProperty().isNotEmpty());
            value.managedProperty().bind(value.visibleProperty());
        }

        metrics.getStyleClass().add("report-metrics");
        show(metrics, false);

        rowSearch.getStyleClass().add("report-search");
        rowSearch.setPromptText("Filter rows…");
        rowSearch.setAccessibleText("Find in report table");
        rowSearch.setPrefWidth(200);
        rowSearch.setMinWidth(120);
        rowSearch.textProperty().addListener((property, before, after) -> {
            show(clearSearch, after != null && !after.isEmpty());
            filterCurrent.accept(after == null ? "" : after);
        });

        clearSearch.getStyleClass().add("report-chip-btn");
        clearSearch.setOnAction(e -> rowSearch.clear());
        show(clearSearch, false);

        ToggleGroup modeGroup = new ToggleGroup();
        tableToggle.setToggleGroup(modeGroup);
        chartToggle.setToggleGroup(modeGroup);
        tableToggle.setSelected(true);
        tableToggle.getStyleClass().add("report-seg-btn");
        chartToggle.getStyleClass().add("report-seg-btn");
        viewSwitch.getStyleClass().add("report-seg-group");
        viewSwitch.setAlignment(Pos.CENTER_LEFT);
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) oldToggle.setSelected(true);
                return;
            }
            displayCurrent.run();
        });

        show(rowSearch, false);
        show(viewSwitch, false);

        searchNote.setWrapText(true);
        searchNote.visibleProperty().bind(searchNote.textProperty().isNotEmpty());
        searchNote.managedProperty().bind(searchNote.visibleProperty());

        resultsPane.getStyleClass().add("report-result-body");
        VBox.setVgrow(resultsPane, Priority.ALWAYS);

        VBox results = card(
                line(resultsTitle, spacer(), resultCount, rowSearch, clearSearch, viewSwitch),
                summary,
                searchNote,
                resultsPane);
        VBox.setVgrow(results, Priority.ALWAYS);

        getChildren().addAll(header, controls, notice, metrics, results);

        reportChoice.setOnAction(event -> selectReport());
        reportChoice.getSelectionModel().selectFirst();
    }

    private void setupDatePresets() {
        datePresets.setAlignment(Pos.CENTER_LEFT);
        Button d7 = presetChip("7D", () -> applyDateRange(LocalDate.now().minusDays(6), LocalDate.now()));
        Button mtd = presetChip("MTD", () -> applyDateRange(LocalDate.now().withDayOfMonth(1), LocalDate.now()));
        Button d30 = presetChip("30D", () -> applyDateRange(LocalDate.now().minusDays(29), LocalDate.now()));
        Button ytd = presetChip("YTD", () -> applyDateRange(LocalDate.now().withDayOfYear(1), LocalDate.now()));
        datePresets.getChildren().addAll(d7, mtd, d30, ytd);

        Runnable syncChips = () -> {
            LocalDate f = from.getValue();
            LocalDate t = to.getValue();
            LocalDate today = LocalDate.now();
            setChipActive(d7, today.minusDays(6).equals(f) && today.equals(t));
            setChipActive(mtd, today.withDayOfMonth(1).equals(f) && today.equals(t));
            setChipActive(d30, today.minusDays(29).equals(f) && today.equals(t));
            setChipActive(ytd, today.withDayOfYear(1).equals(f) && today.equals(t));
        };
        from.valueProperty().addListener((obs, o, n) -> syncChips.run());
        to.valueProperty().addListener((obs, o, n) -> syncChips.run());
        syncChips.run();
    }

    private void applyDateRange(LocalDate start, LocalDate end) {
        from.setValue(start);
        to.setValue(end);
        if (opened && reportChoice.getValue() != null && reportChoice.getValue().scope() == Scope.DATES) {
            load();
        }
    }

    private static Button presetChip(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("report-chip-btn");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private static void setChipActive(Button chip, boolean active) {
        chip.getStyleClass().remove("report-chip-btn-active");
        if (active) chip.getStyleClass().add("report-chip-btn-active");
    }

    public void onOpen() {
        if (opened) return;
        opened = true;
        selectReport();
    }

    private void selectReport() {
        Report<?> report = reportChoice.getValue();
        if (report == null) return;
        requestNumber++;
        run.setDisable(false);
        description.setText(report.description());
        scopeLabel.setText(switch (report.scope()) {
            case SNAPSHOT -> "LIVE SNAPSHOT";
            case ALL_TIME -> "ALL-TIME LEDGER";
            case DATES -> "DATE RANGE · INCLUSIVE";
            case SERIAL -> "UNIT SERIAL AUDIT";
        });
        show(range, report.scope() == Scope.DATES);
        show(serialFilter, report.scope() == Scope.SERIAL);
        run.setText(switch (report.scope()) {
            case DATES -> "Run report";
            case SERIAL -> "Look up";
            default -> "Refresh";
        });
        if (!opened) return;
        setNotice("", false);
        clearResults();
        if (report.scope() == Scope.SERIAL) {
            showTable(report, List.of(), "Enter a serial number above to inspect unit status, warranty, and movement history.");
            serial.requestFocus();
        } else {
            if (report.scope() == Scope.DATES) {
                showTable(report, List.of(), "Choose a date range and run the report.");
            }
            load();
        }
    }

    private void load() {
        Report<?> report = reportChoice.getValue();
        if (report == null || run.isDisabled()) return;
        if (report.scope() == Scope.DATES) {
            try {
                from.commitValue();
                to.commitValue();
            } catch (RuntimeException invalid) {
                setNotice("Enter a valid From and To date using the date picker.", true);
                return;
            }
            if (from.getValue() == null || to.getValue() == null || from.getValue().isAfter(to.getValue())) {
                setNotice("Choose both dates. From must be on or before To.", true);
                return;
            }
        }
        String serialText = serial.getText().strip();
        if (report.scope() == Scope.SERIAL && serialText.isEmpty()) {
            setNotice("Enter a serial number to look up.", true);
            return;
        }
        request(report, new Query(from.getValue(), to.getValue(), serialText));
    }

    private <T> void request(Report<T> report, Query query) {
        long request = ++requestNumber;
        run.setDisable(true);
        clearResults();
        setNotice("", false);
        showTable(report, List.of(), "Loading report…");
        report.load().apply(query).whenComplete((data, error) -> Platform.runLater(() -> {
            if (request != requestNumber) return;
            run.setDisable(false);
            if (error != null) {
                showTable(report, List.of(), "Couldn’t load this report.");
                setNotice(message(error), true);
                return;
            }
            displayResults(report, query, data, request);
        }));
    }

    private void clearResults() {
        summary.setText("");
        resultCount.setText("");
        show(resultCount, false);
        searchNote.setText("");
        resultsTitle.setText("Results");
        metrics.getChildren().clear();
        show(metrics, false);
        filterCurrent = ignored -> {};
        displayCurrent = () -> {};
        rowSearch.clear();
        show(rowSearch, false);
        show(clearSearch, false);
        tableToggle.setSelected(true);
        show(viewSwitch, false);
        exportCurrent = null;
        export.setDisable(true);
    }

    private <T> void displayResults(Report<T> report, Query query, Result<T> data, long request) {
        summary.setText(data.summary());
        for (Metric metric : data.metrics()) metrics.getChildren().add(metricCard(metric));
        show(metrics, !data.metrics().isEmpty());

        TableView<T> table = showTable(report, List.of(), data.emptyMessage());
        int total = data.rows().size();
        String period = report.scope() == Scope.DATES
                ? DAY.format(query.from()) + " – " + DAY.format(query.to()) + "  ·  " : "";
        resultCount.setText(period + total + (total == 1 ? " row" : " rows"));
        show(resultCount, true);
        if (total == 0) return;

        var filtered = new FilteredList<>(FXCollections.observableArrayList(data.rows()));
        var sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        show(rowSearch, true);
        show(viewSwitch, data.chart() != null);
        exportCurrent = () -> export(report, List.copyOf(table.getItems()), request);
        export.setDisable(exporting);

        filterCurrent = input -> {
            String queryText = input.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(row -> queryText.isEmpty() || report.columns().stream()
                    .anyMatch(column -> matches(column, row, queryText)));
            int visible = filtered.size();
            resultCount.setText(period + (queryText.isEmpty() ? total + (total == 1 ? " row" : " rows")
                    : visible + " of " + total + " rows"));
            ((Label) table.getPlaceholder()).setText(queryText.isEmpty() ? data.emptyMessage()
                    : "No results match your filter.");
            searchNote.setText(queryText.isEmpty() ? "" : (data.chart() != null
                    ? "Search filters the table and CSV export. KPI cards and charts reflect the full dataset."
                    : "CSV export includes only the matching rows."));
            export.setDisable(exporting || visible == 0);
        };

        if (data.chart() != null) {
            displayCurrent = new Runnable() {
                private Node chart;
                @Override public void run() {
                    boolean chartActive = chartToggle.isSelected();
                    resultsTitle.setText(chartActive ? "Analytics Chart" : "Results");
                    if (chartActive && chart == null) {
                        chart = data.chart().build();
                        VBox.setVgrow(chart, Priority.ALWAYS);
                    }
                    resultsPane.getChildren().setAll(chartActive ? chart : table);
                }
            };
        }
    }

    private static <T, V> boolean matches(Column<T, V> column, T row, String query) {
        V value = column.value().apply(row);
        return value != null && (column.display().apply(value).toLowerCase(Locale.ROOT).contains(query)
                || value.toString().toLowerCase(Locale.ROOT).contains(query));
    }

    private static VBox metricCard(Metric metric) {
        String accentClass = switch (metric.accent()) {
            case COBALT -> "report-metric-cobalt";
            case EMERALD -> "report-metric-emerald";
            case AMBER -> "report-metric-amber";
            case CRIMSON -> "report-metric-crimson";
        };
        VBox card = new VBox(5,
                label(metric.label().toUpperCase(Locale.ROOT), "report-metric-label"),
                label(metric.value(), "report-metric-value"));
        card.getStyleClass().addAll("report-metric", accentClass);
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private <T> TableView<T> showTable(Report<T> report, List<T> items, String placeholder) {
        TableView<T> table = new TableView<>();
        table.getStyleClass().add("report-table");
        table.setAccessibleText(report.name() + " results");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(placeholder));
        table.setMinWidth(0);
        table.setMinHeight(150);
        report.columns().forEach(definition -> table.getColumns().add(tableColumn(definition)));
        table.getItems().setAll(items);
        VBox.setVgrow(table, Priority.ALWAYS);
        resultsPane.getChildren().setAll(table);
        return table;
    }

    private <T> void export(Report<T> report, List<T> rows, long request) {
        var chooser = new FileChooser();
        chooser.setTitle("Export " + report.name());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName(report.name().toLowerCase(Locale.ROOT).replace(' ', '-') + ".csv");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        exporting = true;
        export.setDisable(true);
        Path destination = file.toPath();
        CompletableFuture.runAsync(() -> {
            try {
                writeCsv(destination, report.columns().stream().map(Column::heading).toList(),
                        rows.stream().map(row -> report.columns().stream()
                                .<Object>map(column -> column.value().apply(row)).toList()));
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }).whenComplete((unused, error) -> Platform.runLater(() -> {
            exporting = false;
            if (exportCurrent == null) export.setDisable(true);
            else filterCurrent.accept(rowSearch.getText());
            if (request != requestNumber) return;
            setNotice(error == null ? "Exported " + file.getName() : "Couldn’t save the CSV. Choose a writable folder.", error != null);
        }));
    }

    static void writeCsv(Path destination, List<String> headings, java.util.stream.Stream<List<Object>> rows)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeLine(writer, headings);
            var iterator = rows.iterator();
            while (iterator.hasNext()) writeLine(writer, iterator.next());
        }
    }

    private static void writeLine(BufferedWriter writer, List<?> values) throws IOException {
        boolean first = true;
        for (Object value : values) {
            if (!first) writer.write(',');
            first = false;
            writer.write(csvCell(value));
        }
        writer.write("\r\n");
    }

    private static String csvCell(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof Number) return value.toString();
        String text = value.toString();
        String trimmed = text.stripLeading();
        if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
                || (!text.isEmpty() && "\t\r\n".indexOf(text.charAt(0)) >= 0)) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private List<Report<?>> reports() {
        return List.of(
            new Report<StockItem>("Inventory valuation",
                "Quantities and stock value at cost, with current selling price for comparison.", Scope.SNAPSHOT,
                List.of(sku("SKU", 122, StockItem::sku), strong("Product", 250, StockItem::productName),
                    number("On hand", 100, StockItem::stockQuantity), money("Stock value (MAD)", 160, StockItem::stockValueMad),
                    money("Selling price (MAD)", 165, StockItem::sellingPriceMad)),
                query -> inventory.fetchInventoryValuation().thenApply(rows -> result(rows, List.of(
                    metric("Stock value at cost", mad(sum(rows, StockItem::stockValueMad)), Accent.COBALT),
                    metric("Units on hand", formatInt(total(rows, StockItem::stockQuantity)), Accent.EMERALD),
                    metric("Products counted", formatInt(rows.size()), Accent.AMBER)),
                    ReportCharts.rank("Stock value by product", rows, StockItem::sku, StockItem::stockValueMad,
                            false, ReportCharts.Tone.COBALT)))),
            new Report<LowStockItem>("Low stock",
                "Active products at or below their threshold, including products with zero stock.", Scope.SNAPSHOT,
                List.of(sku("SKU", 125, LowStockItem::sku), strong("Product", 280, LowStockItem::productName),
                    stockAlert("On hand", 125, LowStockItem::stockQuantity),
                    number("Alert threshold", 140, LowStockItem::lowStockThreshold)),
                query -> inventory.fetchLowStock().thenApply(rows -> {
                    long out = rows.stream().filter(row -> row.stockQuantity() <= 0).count();
                    return new Result<>(rows, List.of(
                            metric("Products with alerts", formatInt(rows.size()), Accent.AMBER),
                            metric("Low but in stock", formatInt(rows.size() - out), Accent.COBALT),
                            metric("Out of stock", formatInt(out), Accent.CRIMSON)),
                            "", "No low-stock alerts.", null);
                })),
            new Report<Movement>("Stock movements",
                "Signed quantity and value changes across products in the selected period.", Scope.DATES,
                List.of(datetime("Time", 150, Movement::movementAt), sku("SKU", 105, Movement::sku),
                    strong("Product", 165, Movement::productName), kind("Movement", 145, Movement::movementType),
                    deltaQty("Qty Δ", 95, Movement::quantityChange), deltaMoney("Value Δ (MAD)", 135, Movement::valueChangeMad),
                    text("Serial", 115, Movement::serialNumber), text("Details", 185, m ->
                        movementDetails(m.adjustmentReason(), m.explanation(), m.reversalOfMovementId()))),
                query -> api.movements(query.from(), query.to()).thenApply(rows -> result(rows, List.of(
                    metric("Total movements", formatInt(rows.size()), Accent.COBALT),
                    metric("Units added", "+" + formatInt(total(rows, row -> Math.max(0, row.quantityChange()))), Accent.EMERALD),
                    metric("Units removed", "−" + formatInt(total(rows, row -> Math.max(0L, -(long) row.quantityChange()))), Accent.CRIMSON)), null))),
            new Report<SupplierPurchases>("Purchases by supplier",
                "Confirmed purchases and their totals in MAD, grouped by supplier.", Scope.ALL_TIME,
                List.of(strong("Supplier", 300, SupplierPurchases::supplierName),
                    number("Confirmed purchases", 180, SupplierPurchases::confirmedPurchases),
                    money("Purchased (MAD)", 175, SupplierPurchases::totalPurchasedMad)),
                query -> api.purchasesBySupplier().thenApply(rows -> result(rows, List.of(
                    metric("Total purchased", mad(sum(rows, SupplierPurchases::totalPurchasedMad)), Accent.COBALT),
                    metric("Confirmed purchases", formatInt(total(rows, SupplierPurchases::confirmedPurchases)), Accent.EMERALD),
                    metric("Active suppliers", formatInt(rows.size()), Accent.AMBER)),
                    ReportCharts.rank("Purchases by supplier · spend", rows,
                            SupplierPurchases::supplierName, SupplierPurchases::totalPurchasedMad,
                            false, ReportCharts.Tone.COBALT)))),
            new Report<ProductSales>("Sales by product",
                "Units sold and revenue from confirmed sales, grouped by product.", Scope.ALL_TIME,
                List.of(sku("SKU", 125, ProductSales::sku), strong("Product", 285, ProductSales::productName),
                    number("Units sold", 125, ProductSales::unitsSold), money("Revenue (MAD)", 165, ProductSales::revenueMad)),
                query -> api.salesByProduct().thenApply(rows -> result(rows, List.of(
                    metric("Total revenue", mad(sum(rows, ProductSales::revenueMad)), Accent.COBALT),
                    metric("Units sold", formatInt(total(rows, ProductSales::unitsSold)), Accent.EMERALD),
                    metric("Products sold", formatInt(rows.size()), Accent.AMBER)),
                    ReportCharts.rank("Revenue by product", rows, ProductSales::sku, ProductSales::revenueMad,
                            false, ReportCharts.Tone.COBALT)))),
            new Report<CategorySales>("Sales by category",
                "Units sold and revenue from confirmed sales, grouped by category.", Scope.ALL_TIME,
                List.of(strong("Category", 300, CategorySales::categoryName), number("Units sold", 135, CategorySales::unitsSold),
                    money("Revenue (MAD)", 175, CategorySales::revenueMad)),
                query -> api.salesByCategory().thenApply(rows -> result(rows, List.of(
                    metric("Total revenue", mad(sum(rows, CategorySales::revenueMad)), Accent.COBALT),
                    metric("Units sold", formatInt(total(rows, CategorySales::unitsSold)), Accent.EMERALD),
                    metric("Categories", formatInt(rows.size()), Accent.AMBER)),
                    ReportCharts.rank("Revenue by category", rows, CategorySales::categoryName, CategorySales::revenueMad,
                            false, ReportCharts.Tone.COBALT)))),
            new Report<ProductProfit>("Gross profit by product",
                "Revenue less cost of goods sold, grouped by product. Operating expenses aren’t included.", Scope.ALL_TIME,
                List.of(sku("SKU", 120, ProductProfit::sku), strong("Product", 250, ProductProfit::productName),
                    money("Revenue (MAD)", 140, ProductProfit::revenueMad), money("COGS (MAD)", 135, ProductProfit::costMad),
                    profit("Gross profit (MAD)", 170, ProductProfit::grossProfitMad)),
                query -> api.profitByProduct().thenApply(rows -> {
                    BigDecimal gross = sum(rows, ProductProfit::grossProfitMad);
                    return result(rows, List.of(
                            metric("Gross profit", mad(gross), gross.signum() < 0 ? Accent.CRIMSON : Accent.EMERALD),
                            metric("Revenue", mad(sum(rows, ProductProfit::revenueMad)), Accent.COBALT),
                            metric("Cost of goods sold", mad(sum(rows, ProductProfit::costMad)), Accent.AMBER)),
                            ReportCharts.rank("Gross profit by product", rows, ProductProfit::sku, ProductProfit::grossProfitMad,
                                    true, ReportCharts.Tone.EMERALD));
                })),
            new Report<SalesDay>("Daily sales",
                "Units sold, revenue and gross profit from confirmed sales by sale date.", Scope.DATES,
                List.of(strongDate("Date", 165, SalesDay::date), number("Units sold", 140, SalesDay::unitsSold),
                    money("Revenue (MAD)", 165, SalesDay::revenueMad), profit("Gross profit (MAD)", 175, SalesDay::profitMad)),
                query -> inventory.fetchDailySales(query.from(), query.to()).thenApply(rows -> {
                    BigDecimal profitTotal = sum(rows, SalesDay::profitMad);
                    return result(rows, List.of(
                            metric("Revenue", mad(sum(rows, SalesDay::revenueMad)), Accent.COBALT),
                            metric("Gross profit", mad(profitTotal), profitTotal.signum() < 0 ? Accent.CRIMSON : Accent.EMERALD),
                            metric("Units sold", formatInt(total(rows, SalesDay::unitsSold)), Accent.AMBER)),
                            ReportCharts.daily(query.from(), query.to(), rows));
                })),
            new Report<StockMovement>("Serial trace",
                "Look up a tracked unit to see its status, warranty dates and movement history.", Scope.SERIAL,
                List.of(datetime("Time", 160, StockMovement::movementAt), kind("Movement", 150, StockMovement::movementType),
                    deltaQty("Qty change", 105, StockMovement::quantityChange), deltaMoney("Value Δ (MAD)", 140, StockMovement::valueChangeMad),
                    text("Details", 260, m -> movementDetails(m.adjustmentReason(), m.explanation(), m.reversalOfMovementId()))),
                query -> api.traceSerial(query.serial()).thenApply(trace -> trace.map(unit ->
                        new Result<>(unit.movements() == null ? List.of() : unit.movements(),
                                traceMetrics(unit), traceIdentity(unit),
                                "No movements recorded for this unit.", null))
                    .orElseGet(() -> new Result<>(List.of(), List.of(), "",
                            "No unit found for serial " + query.serial() + ".", null))))
        );
    }

    private static String traceIdentity(SerialTrace unit) {
        return unit.productName() + "  (" + unit.sku() + ")   ·   Serial #" + unit.serialNumber();
    }

    private static List<Metric> traceMetrics(SerialTrace unit) {
        String statusText = friendly(unit.status());
        Accent statusAccent = switch (unit.status() == null ? "" : unit.status().toUpperCase(Locale.ROOT)) {
            case "IN_STOCK", "AVAILABLE" -> Accent.EMERALD;
            case "SOLD", "HANDED_OVER" -> Accent.COBALT;
            case "RESERVED" -> Accent.AMBER;
            default -> Accent.CRIMSON;
        };
        String warranty = unit.warrantyStart() == null ? "Not started"
                : DAY.format(unit.warrantyStart()) + (unit.warrantyEnd() == null ? "" : " → " + DAY.format(unit.warrantyEnd()));
        return List.of(
                metric("Current status", statusText, statusAccent),
                metric("Acquisition cost", unit.acquisitionCostMad() == null ? "—" : mad(unit.acquisitionCostMad()), Accent.COBALT),
                metric("Warranty coverage", warranty, Accent.AMBER));
    }

    private static String movementDetails(String reason, String explanation, Long reversalId) {
        var parts = new ArrayList<String>(3);
        if (reason != null && !reason.isBlank()) parts.add(friendly(reason));
        if (explanation != null && !explanation.isBlank()) parts.add(explanation);
        if (reversalId != null) parts.add("Reverses #" + reversalId);
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static <T> Result<T> result(List<T> rows, List<Metric> metrics, ReportCharts.Model chart) {
        return new Result<>(rows, metrics, "", "No rows for this report.", chart);
    }

    private static Metric metric(String label, String value, Accent accent) {
        return new Metric(label, value, accent);
    }

    private static String formatInt(long value) {
        return NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value);
    }

    private static <T> long total(List<T> rows, ToLongFunction<T> quantity) {
        return rows.stream().mapToLong(quantity).sum();
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String amount(BigDecimal value) {
        if (value == null) return "—";
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(4);
        return format.format(value);
    }

    private static String signedAmount(BigDecimal value) {
        if (value == null) return "—";
        if (value.signum() > 0) return "+" + amount(value);
        return amount(value);
    }

    private static String mad(BigDecimal value) { return amount(value) + " MAD"; }

    private static String friendly(String value) {
        if (value == null || value.isBlank()) return "—";
        String lower = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static <T> Column<T, String> text(String name, int width, Function<T, String> value) {
        return new Column<>(name, width, false, CellKind.PLAIN, value, Function.identity());
    }

    private static <T> Column<T, String> strong(String name, int width, Function<T, String> value) {
        return new Column<>(name, width, false, CellKind.STRONG, value, Function.identity());
    }

    private static <T> Column<T, String> sku(String name, int width, Function<T, String> value) {
        return new Column<>(name, width, false, CellKind.SKU, value, Function.identity());
    }

    private static <T> Column<T, String> kind(String name, int width, Function<T, String> value) {
        return new Column<>(name, width, false, CellKind.MOVEMENT, value, ReportsView::friendly);
    }

    private static <T, N extends Number> Column<T, N> number(String name, int width, Function<T, N> value) {
        return new Column<>(name, width, true, CellKind.PLAIN, value, Number::toString);
    }

    private static <T, N extends Number> Column<T, N> stockAlert(String name, int width, Function<T, N> value) {
        return new Column<>(name, width, true, CellKind.STOCK_ALERT, value,
                q -> q.longValue() <= 0 ? "0 · Out" : q.longValue() + " · Low");
    }

    private static <T, N extends Number> Column<T, N> deltaQty(String name, int width, Function<T, N> value) {
        return new Column<>(name, width, true, CellKind.DELTA_QTY, value,
                x -> x.longValue() > 0 ? "+" + x.longValue() : Long.toString(x.longValue()));
    }

    private static <T> Column<T, BigDecimal> deltaMoney(String name, int width, Function<T, BigDecimal> value) {
        return new Column<>(name, width, true, CellKind.DELTA_MONEY, value, ReportsView::signedAmount);
    }

    private static <T> Column<T, BigDecimal> money(String name, int width, Function<T, BigDecimal> value) {
        return new Column<>(name, width, true, CellKind.AMOUNT, value, ReportsView::amount);
    }

    private static <T> Column<T, BigDecimal> profit(String name, int width, Function<T, BigDecimal> value) {
        return new Column<>(name, width, true, CellKind.PROFIT, value, ReportsView::amount);
    }

    private static <T> Column<T, LocalDate> strongDate(String name, int width, Function<T, LocalDate> value) {
        return new Column<>(name, width, false, CellKind.STRONG, value, DAY::format);
    }

    private static <T> Column<T, LocalDateTime> datetime(String name, int width, Function<T, LocalDateTime> value) {
        return new Column<>(name, width, false, CellKind.PLAIN, value, TIME::format);
    }

    private static <T, V> TableColumn<T, V> tableColumn(Column<T, V> definition) {
        TableColumn<T, V> column = new TableColumn<>(definition.heading());
        column.setPrefWidth(definition.width());
        column.setMinWidth(Math.min(definition.width(), 85));
        if (definition.alignRight()) {
            column.getStyleClass().add("report-col-numeric");
        }
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(definition.value().apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(V value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("report-cell-strong", "report-cell-amount",
                        "report-profit-pos", "report-profit-neg");
                setGraphic(null);
                setText(null);
                setAlignment(definition.alignRight() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

                if (empty) return;
                if (value == null) {
                    setText("—");
                    return;
                }
                String text = definition.display().apply(value);
                setTooltip(text == null || text.length() < 12 ? null : new Tooltip(text));

                switch (definition.kind()) {
                    case SKU -> {
                        if (text == null || text.isBlank() || "—".equals(text)) {
                            setText("—");
                        } else {
                            setGraphic(pill(text, "report-sku-tag"));
                        }
                    }
                    case MOVEMENT -> {
                        String raw = value.toString().toUpperCase(Locale.ROOT);
                        String tone = raw.contains("PURCHASE") || raw.contains("IN") || raw.contains("ADD")
                                ? "report-badge-emerald"
                                : raw.contains("SALE") || raw.contains("OUT") || raw.contains("DEDUCT")
                                ? "report-badge-cobalt"
                                : raw.contains("CANCEL") || raw.contains("REVERS")
                                ? "report-badge-crimson"
                                : "report-badge-amber";
                        setGraphic(pill(text, "report-badge", tone));
                    }
                    case DELTA_QTY -> {
                        long qty = ((Number) value).longValue();
                        String tone = qty > 0 ? "report-badge-emerald"
                                : qty < 0 ? "report-badge-crimson" : "report-badge-neutral";
                        setGraphic(pill(text, "report-badge", tone));
                    }
                    case DELTA_MONEY -> {
                        BigDecimal amt = (BigDecimal) value;
                        String tone = amt.signum() > 0 ? "report-badge-emerald"
                                : amt.signum() < 0 ? "report-badge-crimson" : "report-badge-neutral";
                        setGraphic(pill(text, "report-badge", tone));
                    }
                    case STOCK_ALERT -> {
                        long qty = ((Number) value).longValue();
                        String tone = qty <= 0 ? "report-badge-crimson" : "report-badge-amber";
                        setGraphic(pill(text, "report-badge", tone));
                    }
                    case PROFIT -> {
                        BigDecimal amt = (BigDecimal) value;
                        setText(text);
                        if (amt.signum() > 0) getStyleClass().add("report-profit-pos");
                        else if (amt.signum() < 0) getStyleClass().add("report-profit-neg");
                        else getStyleClass().add("report-cell-amount");
                    }
                    case STRONG -> {
                        setText(text);
                        getStyleClass().add("report-cell-strong");
                    }
                    case AMOUNT -> {
                        setText(text);
                        getStyleClass().add("report-cell-amount");
                    }
                    default -> setText(text);
                }
            }
        });
        return column;
    }

    private static Label pill(String text, String... classes) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll(classes);
        badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return badge;
    }

    private void setNotice(String value, boolean error) {
        notice.getStyleClass().remove("report-error");
        if (error) notice.getStyleClass().add("report-error");
        notice.setText(value);
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        if (error instanceof ConnectException) return "Check that the backend is running, then retry.";
        if (error instanceof HttpTimeoutException) return "The backend timed out. Try again.";
        String detail = error.getMessage();
        if (error instanceof IllegalStateException && detail != null) {
            int httpIndex = detail.lastIndexOf("HTTP ");
            if (httpIndex >= 0) {
                return "The backend returned " + detail.substring(httpIndex) + ". Try again.";
            }
        }
        return "Couldn’t read the report. Check the backend and try again.";
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static VBox card(Node... nodes) {
        VBox card = new VBox(12, nodes);
        card.getStyleClass().add("report-card");
        return card;
    }

    private static VBox labeled(String text, Node field) {
        Label name = label(text, "report-field");
        name.setLabelFor(field);
        return new VBox(5, name, field);
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static HBox line(Node... nodes) {
        HBox box = new HBox(10, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}