package com.electrostock.frontend.view;

import com.electrostock.frontend.api.InventoryReportClient;
import com.electrostock.frontend.api.InventoryReportClient.LowStockItem;
import com.electrostock.frontend.api.InventoryReportClient.StockMovement;
import com.electrostock.frontend.api.ProductApiClient;
import com.electrostock.frontend.api.ProductApiClient.Product;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Stock availability, adjustments and movement history. Quantities come from the backend. */
public final class StockView extends VBox {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private enum Level {
        ALL("All products", "All", "all"),
        IN_STOCK("In stock", "Healthy", "in"),
        LOW("Low stock", "Low", "low"),
        OUT("Out of stock", "Out", "out");

        private final String label;
        private final String chipLabel;
        private final String style;

        Level(String label, String chipLabel, String style) {
            this.label = label;
            this.chipLabel = chipLabel;
            this.style = style;
        }

        @Override public String toString() { return label; }
    }

    private enum MovementDirection {
        ALL("All"),
        INBOUND("Inbound (+)"),
        OUTBOUND("Outbound (−)");

        private final String label;

        MovementDirection(String label) {
            this.label = label;
        }
    }

    private record StockLoad(List<LowStockItem> availability, List<Product> products) {}

    private final InventoryReportClient api = new InventoryReportClient();
    private final ProductApiClient productApi = new ProductApiClient();
    private final Map<Long, Product> productById = new HashMap<>();
    private boolean adjustmentReady;
    private final TextField search = new TextField();
    private final Button clearSearch = new Button("Clear");
    private final Button export = new Button("Export CSV");
    private final Button refresh = new Button("Refresh");
    private final Button adjust = new Button("Adjust stock");
    private final Label notice = label("", "stock-notice");
    private final Label resultCount = label("", "stock-count-pill");
    private final Label totalUnitsBadge = label("", "stock-units-pill");

    private final Label movementSkuBadge = label("", "stock-sku-tag");
    private final Label movementProduct = label("Select a product above", "stock-muted");
    private final Label movementStats = label("", "stock-movement-stats");

    private final Label stockPlaceholder = new Label("Loading stock…");
    private final Label movementPlaceholder = new Label("Select a product to view its movements.");

    private final EnumMap<Level, Label> totals = new EnumMap<>(Level.class);
    private final EnumMap<Level, Label> subTotals = new EnumMap<>(Level.class);
    private final EnumMap<Level, VBox> metricCards = new EnumMap<>(Level.class);
    private final EnumMap<Level, ToggleButton> levelTabs = new EnumMap<>(Level.class);
    private final EnumMap<MovementDirection, ToggleButton> movementTabs = new EnumMap<>(MovementDirection.class);

    private final TableView<LowStockItem> stockTable = new TableView<>();
    private final TableView<StockMovement> movementTable = new TableView<>();
    private final ObservableList<LowStockItem> visibleRows = FXCollections.observableArrayList();
    private final ObservableList<StockMovement> allMovements = FXCollections.observableArrayList();
    private final FilteredList<StockMovement> filteredMovements = new FilteredList<>(allMovements);

    private Level activeLevel = Level.ALL;
    private MovementDirection activeDirection = MovementDirection.ALL;
    private List<LowStockItem> snapshot = List.of();
    private boolean loading;
    private boolean loaded;
    private boolean exporting;
    private boolean replacingRows;
    private boolean syncingFilterControls;
    private Long movementProductId;
    private long movementRequest;

    public StockView() {
        getStyleClass().add("stock-page");
        var css = StockView.class.getResource("stock.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
        setMinWidth(0);

        VBox title = new VBox(3,
                label("Stock Command & Availability", "stock-title"),
                label("Monitor live inventory levels, reorder thresholds, and unit movement trails.", "stock-muted"));

        export.getStyleClass().add("stock-secondary");
        export.setDisable(true);
        export.setTooltip(new Tooltip("Export the currently filtered stock rows to CSV."));
        export.setOnAction(event -> exportCsv());

        refresh.getStyleClass().add("stock-primary");
        refresh.setOnAction(event -> onOpen());
        adjust.getStyleClass().add("stock-secondary");
        adjust.setOnAction(event -> openAdjustment());
        updateAdjustmentAction(null);

        HBox actions = line(export, refresh);
        HBox header = line(title, spacer(), actions);

        HBox cards = new HBox(12,
                metric("ACTIVE CATALOG", Level.ALL),
                metric("HEALTHY STOCK", Level.IN_STOCK),
                metric("LOW STOCK ALERTS", Level.LOW),
                metric("OUT OF STOCK", Level.OUT));

        // Segmented Level Tabs
        HBox levelSwitch = new HBox(2);
        levelSwitch.getStyleClass().add("stock-seg-group");
        levelSwitch.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup levelGroup = new ToggleGroup();
        for (Level level : Level.values()) {
            ToggleButton btn = new ToggleButton(level.chipLabel);
            btn.setToggleGroup(levelGroup);
            btn.getStyleClass().addAll("stock-seg-btn", "stock-seg-" + level.style);
            if (level == Level.ALL) btn.setSelected(true);
            btn.setOnAction(e -> setActiveLevel(level));
            levelTabs.put(level, btn);
            levelSwitch.getChildren().add(btn);
        }
        levelGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        search.getStyleClass().add("stock-search");
        search.setPromptText("Search by SKU or product name…");
        search.setAccessibleText("Search stock by product or SKU");
        search.setMinWidth(180);
        HBox.setHgrow(search, Priority.ALWAYS);

        clearSearch.getStyleClass().add("stock-chip-btn");
        clearSearch.setOnAction(e -> search.clear());
        show(clearSearch, false);

        HBox filters = line(levelSwitch, search, clearSearch);

        notice.setWrapText(true);
        notice.visibleProperty().bind(notice.textProperty().isNotEmpty());
        notice.managedProperty().bind(notice.visibleProperty());

        show(resultCount, false);
        show(totalUnitsBadge, false);
        show(movementSkuBadge, false);
        show(movementStats, false);

        configureStockTable();
        configureMovementTable();

        VBox availability = panel(
                line(label("Inventory Availability", "stock-heading"), spacer(), totalUnitsBadge, resultCount),
                filters,
                stockTable);

        // Movement Direction Filter Pills
        HBox directionSwitch = new HBox(2);
        directionSwitch.getStyleClass().add("stock-seg-group");
        directionSwitch.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup directionGroup = new ToggleGroup();
        for (MovementDirection dir : MovementDirection.values()) {
            ToggleButton btn = new ToggleButton(dir.label);
            btn.setToggleGroup(directionGroup);
            btn.getStyleClass().add("stock-seg-btn");
            if (dir == MovementDirection.ALL) btn.setSelected(true);
            btn.setOnAction(e -> {
                activeDirection = dir;
                applyMovementDirectionFilter();
            });
            movementTabs.put(dir, btn);
            directionSwitch.getChildren().add(btn);
        }
        directionGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        HBox selectedProductInfo = new HBox(8, movementSkuBadge, movementProduct);
        selectedProductInfo.setAlignment(Pos.CENTER_LEFT);
        selectedProductInfo.setMinWidth(0);

        VBox history = panel(
                line(label("Movement History", "stock-heading"), selectedProductInfo, spacer(), adjust),
                line(movementStats, spacer(), directionSwitch),
                movementTable);

        SplitPane panels = new SplitPane(availability, history);
        panels.setOrientation(Orientation.VERTICAL);
        panels.setDividerPositions(0.56);
        panels.getStyleClass().add("stock-panels");
        VBox.setVgrow(panels, Priority.ALWAYS);

        getChildren().addAll(header, cards, notice, panels);

        search.textProperty().addListener((property, previous, current) -> {
            show(clearSearch, current != null && !current.isEmpty());
            applyFilters(false);
        });

        stockTable.getSelectionModel().selectedItemProperty().addListener((property, previous, current) -> {
            if (!replacingRows) showMovements(current, false);
        });

        updateCardSelectionStyles();
    }

    public void onOpen() {
        reload(null);
    }

    private void reload(Long adjustedProductId) {
        if (loading) return;
        loading = true;
        adjustmentReady = false;
        refresh.setDisable(true);
        updateAdjustmentAction(stockTable.getSelectionModel().getSelectedItem());
        setNotice(adjustedProductId != null ? "Adjustment saved. Updating stock and movement history…"
                : loaded ? "Refreshing stock snapshot…" : "", false);
        if (!loaded) stockPlaceholder.setText("Loading stock…");

        var catalogue = productApi.products().exceptionally(error -> null);
        api.fetchStockAvailability().thenCombine(catalogue, StockLoad::new)
                .whenComplete((data, error) -> Platform.runLater(() -> {
            loading = false;
            refresh.setDisable(false);
            if (error != null) {
                if (!loaded) stockPlaceholder.setText("Stock could not be loaded.");
                String prefix = adjustedProductId != null ? "Adjustment saved, but stock couldn’t refresh. "
                        : loaded ? "Couldn’t refresh stock. Showing previous data. " : "Couldn’t load stock. ";
                setNotice(prefix + errorMessage(error), true);
                // The saved movement may still be readable even if availability failed.
                if (adjustedProductId != null) {
                    showMovements(stockTable.getSelectionModel().getSelectedItem(), true);
                }
                return;
            }

            loaded = true;
            snapshot = data.availability();
            productById.clear();
            adjustmentReady = data.products() != null;
            if (adjustmentReady) data.products().forEach(product -> productById.put(product.id(), product));

            if (adjustedProductId != null) restoreAdjustedSelection(adjustedProductId);
            updateTotals();
            String success = adjustedProductId == null ? "" : "Stock adjustment saved.";
            setNotice(adjustmentReady ? success : (success.isEmpty() ? "" : success + " ")
                    + "Product details could not be loaded. Refresh to enable stock adjustments.", !adjustmentReady);
            applyFilters(true);
        }));
    }

    private void openAdjustment() {
        LowStockItem row = stockTable.getSelectionModel().getSelectedItem();
        Product product = row == null ? null : productById.get(row.productId());
        if (getScene() == null || !adjustmentReady || loading || product == null
                || !"ACTIVE".equals(product.status())) return;

        if (StockDialogs.adjust(getScene().getWindow(), product, row.stockQuantity(), productApi)) {
            reload(row.productId());
        }
    }

    private void updateAdjustmentAction(LowStockItem row) {
        Product product = row == null ? null : productById.get(row.productId());
        boolean active = product != null && "ACTIVE".equals(product.status());
        show(adjust, product == null || active);
        adjust.setDisable(!adjustmentReady || loading || !active);
        adjust.setTooltip(new Tooltip(row == null ? "Select a product first."
                : !adjustmentReady || product == null ? "Refresh to load current stock and product details."
                : "Add or remove stock for " + product.name() + "."));
    }

    private void restoreAdjustedSelection(long productId) {
        LowStockItem updated = snapshot.stream().filter(row -> row.productId() == productId).findFirst().orElse(null);
        if (updated != null && activeLevel != Level.ALL && levelOf(updated) != activeLevel) {
            // Show the edited row even if it crossed a stock-health threshold.
            activeLevel = Level.ALL;
            levelTabs.get(Level.ALL).setSelected(true);
            updateCardSelectionStyles();
        }
        if (activeDirection != MovementDirection.ALL) {
            activeDirection = MovementDirection.ALL;
            movementTabs.get(MovementDirection.ALL).setSelected(true);
            applyMovementDirectionFilter();
        }
    }

    private void setActiveLevel(Level level) {
        if (syncingFilterControls) return;
        activeLevel = level;
        syncingFilterControls = true;
        try {
            ToggleButton tab = levelTabs.get(level);
            if (tab != null && !tab.isSelected()) tab.setSelected(true);
            updateCardSelectionStyles();
        } finally {
            syncingFilterControls = false;
        }
        applyFilters(false);
    }

    private void updateCardSelectionStyles() {
        metricCards.forEach((level, card) -> {
            card.getStyleClass().remove("stock-metric-selected");
            if (level == activeLevel) {
                card.getStyleClass().add("stock-metric-selected");
            }
        });
    }

    private void configureStockTable() {
        stockTable.getStyleClass().add("stock-table");
        stockTable.setAccessibleText("Stock availability");
        stockTable.setPlaceholder(stockPlaceholder);
        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        stockTable.setMinHeight(150);

        SortedList<LowStockItem> sorted = new SortedList<>(visibleRows);
        sorted.comparatorProperty().bind(stockTable.comparatorProperty());
        stockTable.setItems(sorted);

        // SKU Tag Column
        TableColumn<LowStockItem, String> skuCol = new TableColumn<>("SKU");
        skuCol.setPrefWidth(128);
        skuCol.setMinWidth(100);
        skuCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().sku()));
        skuCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String sku, boolean empty) {
                super.updateItem(sku, empty);
                setGraphic(null);
                setText(null);
                if (empty) return;
                if (sku == null || sku.isBlank()) {
                    setText("—");
                } else {
                    setGraphic(pill(sku, "stock-sku-tag"));
                }
            }
        });
        stockTable.getColumns().add(skuCol);

        // Product Name Column
        TableColumn<LowStockItem, String> nameCol = new TableColumn<>("Product");
        nameCol.setPrefWidth(310);
        nameCol.setMinWidth(180);
        nameCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().productName()));
        nameCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("stock-cell-strong"); }
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(empty ? null : name == null ? "—" : name);
                setTooltip(name == null || name.length() < 26 ? null : new Tooltip(name));
            }
        });
        stockTable.getColumns().add(nameCol);

        // On Hand Column
        TableColumn<LowStockItem, LowStockItem> onHandCol = new TableColumn<>("On hand");
        onHandCol.setPrefWidth(110);
        onHandCol.setMinWidth(90);
        onHandCol.getStyleClass().add("stock-col-numeric");
        onHandCol.setComparator((a, b) -> Long.compare(a.stockQuantity(), b.stockQuantity()));
        onHandCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        onHandCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LowStockItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("stock-qty-in", "stock-qty-low", "stock-qty-out");
                setAlignment(Pos.CENTER_RIGHT);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(formatInt(item.stockQuantity()));
                Level lvl = levelOf(item);
                getStyleClass().add("stock-qty-" + lvl.style);
            }
        });
        stockTable.getColumns().add(onHandCol);

        // Alert Threshold Column
        TableColumn<LowStockItem, Integer> thresholdCol = new TableColumn<>("Alert threshold");
        thresholdCol.setPrefWidth(130);
        thresholdCol.setMinWidth(100);
        thresholdCol.getStyleClass().add("stock-col-numeric");
        thresholdCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().lowStockThreshold()));
        thresholdCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("stock-cell-muted-num"); }
            @Override protected void updateItem(Integer threshold, boolean empty) {
                super.updateItem(threshold, empty);
                setAlignment(Pos.CENTER_RIGHT);
                setText(empty || threshold == null ? null : formatInt(threshold));
            }
        });
        stockTable.getColumns().add(thresholdCol);

        // Stock Level Bar + Status Badge Column
        TableColumn<LowStockItem, LowStockItem> availability = new TableColumn<>("Stock health");
        availability.setPrefWidth(210);
        availability.setMinWidth(175);
        availability.setComparator((a, b) -> {
            int cmp = levelOf(a).compareTo(levelOf(b));
            return cmp != 0 ? cmp : Long.compare(a.stockQuantity(), b.stockQuantity());
        });
        availability.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        availability.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LowStockItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (empty || item == null) return;

                Level level = levelOf(item);
                Label badge = pill(level.label, "stock-badge", "stock-" + level.style);

                // Visual ratio bar relative to 2x threshold (or minimum scale of 10)
                double reference = Math.max(10.0, item.lowStockThreshold() * 2.0);
                double ratio = item.stockQuantity() <= 0 ? 0.0
                        : Math.min(1.0, Math.max(0.08, item.stockQuantity() / reference));

                Region fill = new Region();
                fill.getStyleClass().addAll("stock-bar-fill", "stock-bar-" + level.style);
                fill.setPrefHeight(6);
                fill.setMaxHeight(6);
                fill.setPrefWidth(64 * ratio);
                fill.setMaxWidth(64 * ratio);

                StackPane track = new StackPane(fill);
                track.setAlignment(Pos.CENTER_LEFT);
                track.getStyleClass().add("stock-bar-track");
                track.setPrefSize(64, 6);
                track.setMinSize(64, 6);
                track.setMaxSize(64, 6);

                HBox box = new HBox(10, track, badge);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });
        stockTable.getColumns().add(availability);
    }

    private void configureMovementTable() {
        movementTable.getStyleClass().add("stock-table");
        movementTable.setAccessibleText("Stock movement history");
        movementTable.setPlaceholder(movementPlaceholder);
        movementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movementTable.setMinHeight(140);

        SortedList<StockMovement> sortedMovements = new SortedList<>(filteredMovements);
        sortedMovements.comparatorProperty().bind(movementTable.comparatorProperty());
        movementTable.setItems(sortedMovements);

        // Time Column
        TableColumn<StockMovement, LocalDateTime> date = column("Time", 165, StockMovement::movementAt,
                DATE_TIME::format, null, false);
        movementTable.getColumns().add(date);

        // Movement Type Badge Column
        TableColumn<StockMovement, String> typeCol = new TableColumn<>("Movement");
        typeCol.setPrefWidth(155);
        typeCol.setMinWidth(125);
        typeCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().movementType()));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                setGraphic(null);
                setText(null);
                if (empty) return;
                if (type == null || type.isBlank()) {
                    setText("—");
                    return;
                }
                String raw = type.toUpperCase(Locale.ROOT);
                String tone = raw.contains("PURCHASE") || raw.contains("IN") || raw.contains("ADD")
                        ? "stock-badge-jade"
                        : raw.contains("SALE") || raw.contains("OUT") || raw.contains("DEDUCT")
                        ? "stock-badge-indigo"
                        : raw.contains("CANCEL") || raw.contains("REVERS")
                        ? "stock-badge-rose"
                        : "stock-badge-amber";
                setGraphic(pill(friendlyName(type), "stock-badge", tone));
            }
        });
        movementTable.getColumns().add(typeCol);

        // Signed Quantity Delta Pill Column
        TableColumn<StockMovement, Integer> qtyCol = new TableColumn<>("Qty Δ");
        qtyCol.setPrefWidth(95);
        qtyCol.setMinWidth(80);
        qtyCol.getStyleClass().add("stock-col-numeric");
        qtyCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantityChange()));
        qtyCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer qty, boolean empty) {
                super.updateItem(qty, empty);
                setGraphic(null);
                setText(null);
                setAlignment(Pos.CENTER_RIGHT);
                if (empty || qty == null) return;
                String text = qty > 0 ? "+" + qty : String.valueOf(qty);
                String tone = qty > 0 ? "stock-badge-jade"
                        : qty < 0 ? "stock-badge-rose" : "stock-badge-neutral";
                setGraphic(pill(text, "stock-badge", tone));
            }
        });
        movementTable.getColumns().add(qtyCol);

        // Signed Value Delta (MAD) Column
        TableColumn<StockMovement, BigDecimal> valCol = new TableColumn<>("Value Δ (MAD)");
        valCol.setPrefWidth(135);
        valCol.setMinWidth(110);
        valCol.getStyleClass().add("stock-col-numeric");
        valCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().valueChangeMad()));
        valCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal val, boolean empty) {
                super.updateItem(val, empty);
                getStyleClass().removeAll("stock-val-pos", "stock-val-neg");
                setAlignment(Pos.CENTER_RIGHT);
                if (empty) {
                    setText(null);
                    return;
                }
                if (val == null) {
                    setText("—");
                    return;
                }
                setText(signedAmount(val));
                if (val.signum() > 0) getStyleClass().add("stock-val-pos");
                else if (val.signum() < 0) getStyleClass().add("stock-val-neg");
            }
        });
        movementTable.getColumns().add(valCol);

        // Serial Number Column
        TableColumn<StockMovement, String> serialCol = new TableColumn<>("Serial number");
        serialCol.setPrefWidth(155);
        serialCol.setMinWidth(120);
        serialCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().serialNumber()));
        serialCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String serial, boolean empty) {
                super.updateItem(serial, empty);
                setGraphic(null);
                setText(null);
                if (empty) return;
                if (serial == null || serial.isBlank()) {
                    setText("—");
                } else {
                    setGraphic(pill(serial, "stock-sku-tag"));
                }
            }
        });
        movementTable.getColumns().add(serialCol);

        // Details Column
        TableColumn<StockMovement, StockMovement> detail = new TableColumn<>("Details");
        detail.setPrefWidth(240);
        detail.setSortable(false);
        detail.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        detail.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(StockMovement item, boolean empty) {
                super.updateItem(item, empty);
                String value = empty || item == null ? null : movementDetails(item);
                setText(value);
                setTooltip(value == null || value.equals("—") ? null : new Tooltip(value));
            }
        });
        movementTable.getColumns().add(detail);
    }

    private void applyFilters(boolean refreshHistory) {
        if (!loaded) return;

        LowStockItem selected = stockTable.getSelectionModel().getSelectedItem();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        Level filter = activeLevel;

        var results = snapshot.stream()
                .filter(row -> filter == null || filter == Level.ALL || levelOf(row) == filter)
                .filter(row -> query.isEmpty()
                        || row.sku().toLowerCase(Locale.ROOT).contains(query)
                        || row.productName().toLowerCase(Locale.ROOT).contains(query))
                .toList();

        replacingRows = true;
        try {
            visibleRows.setAll(results);
            if (results.isEmpty()) {
                stockTable.getSelectionModel().clearSelection();
            } else {
                LowStockItem sameProduct = selected == null ? null : results.stream()
                        .filter(row -> row.productId() == selected.productId())
                        .findFirst().orElse(null);
                if (sameProduct == null) {
                    stockTable.getSelectionModel().selectFirst();
                } else {
                    stockTable.getSelectionModel().select(sameProduct);
                }
            }
        } finally {
            replacingRows = false;
        }

        long filteredUnits = results.stream().mapToLong(LowStockItem::stockQuantity).sum();
        totalUnitsBadge.setText(formatInt(filteredUnits) + " units");
        show(totalUnitsBadge, true);

        resultCount.setText(results.size() + " of " + snapshot.size() + " products");
        show(resultCount, true);

        export.setDisable(exporting || results.isEmpty());

        stockPlaceholder.setText(snapshot.isEmpty() ? "No active products."
                : "No products match the current filter.");
        showMovements(stockTable.getSelectionModel().getSelectedItem(), refreshHistory);
    }

    private void showMovements(LowStockItem row, boolean forceRefresh) {
        updateAdjustmentAction(row);
        Long productId = row == null ? null : row.productId();
        if (row == null) {
            show(movementSkuBadge, false);
            movementProduct.setText("Select a product above");
            movementProduct.getStyleClass().remove("stock-cell-strong");
            movementStats.setText("");
            show(movementStats, false);
        } else {
            movementSkuBadge.setText(row.sku());
            show(movementSkuBadge, true);
            movementProduct.setText(row.productName());
            if (!movementProduct.getStyleClass().contains("stock-cell-strong")) {
                movementProduct.getStyleClass().add("stock-cell-strong");
            }
        }

        if (!forceRefresh && Objects.equals(productId, movementProductId)) return;
        movementProductId = productId;
        long request = ++movementRequest;
        allMovements.clear();
        movementStats.setText("");
        show(movementStats, false);

        if (row == null) {
            movementPlaceholder.setText("Select a product to view its movements.");
            return;
        }

        movementPlaceholder.setText("Loading movements…");
        api.fetchStockMovements(row.productId()).whenComplete((items, error) -> Platform.runLater(() -> {
            if (request != movementRequest) return;
            if (error != null) {
                movementPlaceholder.setText("Couldn’t load movements. " + errorMessage(error));
                return;
            }
            allMovements.setAll(items);
            updateMovementSummary(items);
            applyMovementDirectionFilter();
            movementTable.sort();
        }));
    }

    private void updateMovementSummary(List<StockMovement> items) {
        if (items.isEmpty()) {
            movementStats.setText("");
            show(movementStats, false);
            return;
        }
        long added = items.stream().mapToLong(m -> Math.max(0, m.quantityChange())).sum();
        long removed = items.stream().mapToLong(m -> Math.max(0L, -(long) m.quantityChange())).sum();
        movementStats.setText(items.size() + " events  ·  +" + formatInt(added) + " in  ·  −" + formatInt(removed) + " out");
        show(movementStats, true);
    }

    private void applyMovementDirectionFilter() {
        filteredMovements.setPredicate(m -> switch (activeDirection) {
            case ALL -> true;
            case INBOUND -> m.quantityChange() > 0;
            case OUTBOUND -> m.quantityChange() < 0;
        });
        movementPlaceholder.setText(allMovements.isEmpty()
                ? "No stock movements recorded for this product."
                : "No movements match the selected direction.");
    }

    private void updateTotals() {
        var counts = new EnumMap<Level, Integer>(Level.class);
        snapshot.forEach(row -> counts.merge(levelOf(row), 1, Integer::sum));
        counts.put(Level.ALL, snapshot.size());

        long totalUnits = snapshot.stream().mapToLong(LowStockItem::stockQuantity).sum();
        long healthyUnits = snapshot.stream()
                .filter(r -> levelOf(r) == Level.IN_STOCK)
                .mapToLong(LowStockItem::stockQuantity).sum();
        long lowUnits = snapshot.stream()
                .filter(r -> levelOf(r) == Level.LOW)
                .mapToLong(LowStockItem::stockQuantity).sum();

        totals.forEach((level, value) -> value.setText(formatInt(counts.getOrDefault(level, 0))));

        subTotals.get(Level.ALL).setText(formatInt(totalUnits) + " total units on hand");
        subTotals.get(Level.IN_STOCK).setText(formatInt(healthyUnits) + " units above threshold");
        subTotals.get(Level.LOW).setText(formatInt(lowUnits) + " units nearing depletion");
        subTotals.get(Level.OUT).setText(counts.getOrDefault(Level.OUT, 0) == 0
                ? "Zero stockouts" : "Immediate reorder needed");
    }

    private void exportCsv() {
        List<LowStockItem> rowsToExport = List.copyOf(stockTable.getItems());
        if (rowsToExport.isEmpty()) return;

        var chooser = new FileChooser();
        chooser.setTitle("Export Stock Availability");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("stock-availability.csv");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        exporting = true;
        export.setDisable(true);
        Path destination = file.toPath();

        CompletableFuture.runAsync(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write("SKU,Product,On hand,Alert threshold,Status\r\n");
                for (LowStockItem item : rowsToExport) {
                    writer.write(csvCell(item.sku()));
                    writer.write(',');
                    writer.write(csvCell(item.productName()));
                    writer.write(',');
                    writer.write(Long.toString(item.stockQuantity()));
                    writer.write(',');
                    writer.write(Integer.toString(item.lowStockThreshold()));
                    writer.write(',');
                    writer.write(csvCell(levelOf(item).label));
                    writer.write("\r\n");
                }
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }).whenComplete((unused, error) -> Platform.runLater(() -> {
            exporting = false;
            export.setDisable(visibleRows.isEmpty());
            setNotice(error == null
                    ? "Exported " + rowsToExport.size() + " products to " + file.getName()
                    : "Couldn’t save CSV. Choose a writable folder.", error != null);
        }));
    }

    private static String csvCell(String text) {
        if (text == null) return "";
        String trimmed = text.stripLeading();
        if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
                || (!text.isEmpty() && "\t\r\n".indexOf(text.charAt(0)) >= 0)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static Level levelOf(LowStockItem row) {
        if (row.stockQuantity() <= 0) return Level.OUT;
        return row.stockQuantity() <= row.lowStockThreshold() ? Level.LOW : Level.IN_STOCK;
    }

    private static String friendlyName(String value) {
        if (value == null || value.isBlank()) return "—";
        String lower = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String movementDetails(StockMovement movement) {
        var parts = new ArrayList<String>(3);
        String reason = "MISSING".equals(movement.adjustmentReason())
                ? "Lost" : friendlyName(movement.adjustmentReason());
        if (!"—".equals(reason)) parts.add(reason);
        String explanation = movement.explanation();
        if (explanation != null && !explanation.isBlank()) parts.add(explanation);
        if (movement.reversalOfMovementId() != null) {
            parts.add("Reverses #" + movement.reversalOfMovementId());
        }
        return parts.isEmpty() ? "—" : String.join(" · ", parts);
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

    private static String formatInt(long value) {
        return NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value);
    }

    private static String errorMessage(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        if (error instanceof ConnectException) return "Check that the backend is running, then click Refresh.";
        if (error instanceof HttpTimeoutException) return "The backend timed out. Click Refresh to try again.";
        String message = error.getMessage();
        if (error instanceof IllegalStateException && message != null && message.startsWith("GET /api/")) {
            return message + ".";
        }
        return "Check the backend and click Refresh to retry.";
    }

    private void setNotice(String value, boolean error) {
        notice.getStyleClass().remove("stock-error");
        if (error) notice.getStyleClass().add("stock-error");
        notice.setText(value);
    }

    private VBox metric(String title, Level level) {
        Label heading = label(title, "stock-metric-label");
        Label value = label("—", "stock-metric-value");
        Label sub = label("Loading…", "stock-metric-sub");
        totals.put(level, value);
        subTotals.put(level, sub);

        VBox card = new VBox(4, heading, value, sub);
        card.getStyleClass().addAll("stock-metric", "stock-" + level.style);
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(e -> setActiveLevel(level));
        Tooltip.install(card, new Tooltip("Click to filter by " + level.label.toLowerCase(Locale.ROOT)));
        HBox.setHgrow(card, Priority.ALWAYS);
        metricCards.put(level, card);
        return card;
    }

    private static <R, V> TableColumn<R, V> column(String title, int width, Function<R, V> value,
            Function<V, String> display, String style, boolean numeric) {
        TableColumn<R, V> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        if (numeric) column.getStyleClass().add("stock-col-numeric");
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            {
                if (style != null) getStyleClass().add(style);
                setAlignment(numeric ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            }

            @Override protected void updateItem(V item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? "—" : display.apply(item));
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

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static VBox panel(Node header, Node... content) {
        VBox box = new VBox(12, header);
        box.getChildren().addAll(content);
        box.getStyleClass().add("stock-panel");
        box.setMinHeight(165);
        for (Node child : content) {
            if (child instanceof TableView<?>) VBox.setVgrow(child, Priority.ALWAYS);
        }
        return box;
    }

    private static HBox line(Node... nodes) {
        HBox box = new HBox(10, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private static Label label(String value, String style) {
        Label label = new Label(value);
        label.getStyleClass().add(style);
        return label;
    }
}
