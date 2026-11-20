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

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/** Stock availability, adjustments and movement history. Quantities come from the backend. */
public final class StockView extends VBox {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final String SHORTCUT =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") ? "\u2318" : "Ctrl+";

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

        private String tone() {
            return switch (this) {
                case ALL -> "stock-badge-indigo";
                case IN_STOCK -> "stock-badge-jade";
                case LOW -> "stock-badge-amber";
                case OUT -> "stock-badge-rose";
            };
        }

        @Override public String toString() { return label; }
    }

    private enum Direction {
        ALL("All", "all"),
        INBOUND("Inbound", "in"),
        OUTBOUND("Outbound", "out");

        private final String label;
        private final String style;

        Direction(String label, String style) {
            this.label = label;
            this.style = style;
        }

        @Override public String toString() { return label; }
    }

    private enum Workspace {
        AVAILABILITY("Availability"),
        MOVEMENTS("Movement history");

        private final String label;

        Workspace(String label) { this.label = label; }

        @Override public String toString() { return label; }
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
    private final Button historyJump = new Button("Movement log");

    private final Label notice = label("", "stock-notice", "stock-notice-info");
    private final HBox toastTray = new HBox(8);
    private final Label resultCount = label("", "stock-count-pill");
    private final Label totalUnitsBadge = label("", "stock-units-pill");

    private final Label movementSkuBadge = label("", "stock-sku-tag");
    private final Label movementProduct = label("Select a product above", "stock-muted");
    private final Label movementEventsValue = label("—", "stock-stat-value");
    private final Label movementInValue = label("—", "stock-stat-value", "stock-stat-in");
    private final Label movementOutValue = label("—", "stock-stat-value", "stock-stat-out");

    private final Label stockPlaceholder = label("Loading stock…", "stock-placeholder");
    private final Label movementPlaceholder =
            label("Select a product in Availability to see its movement trail.", "stock-placeholder");

    private final EnumMap<Level, Label> totals = new EnumMap<>(Level.class);
    private final EnumMap<Level, Label> subTotals = new EnumMap<>(Level.class);
    private final EnumMap<Level, VBox> metricCards = new EnumMap<>(Level.class);
    private final EnumMap<Level, ToggleButton> levelTabs = new EnumMap<>(Level.class);
    private final EnumMap<Direction, ToggleButton> directionTabs = new EnumMap<>(Direction.class);

    private final TableView<LowStockItem> stockTable = new TableView<>();
    private final TableView<StockMovement> movementTable = new TableView<>();
    private final VBox workspaceBody = new VBox(0);
    private final EnumMap<Workspace, ToggleButton> workspaceTabs = new EnumMap<>(Workspace.class);
    private VBox availabilityPanel;
    private VBox historyPanel;
    private Workspace workspace = Workspace.AVAILABILITY;
    private final ObservableList<LowStockItem> visibleRows = FXCollections.observableArrayList();
    private final ObservableList<StockMovement> allMovements = FXCollections.observableArrayList();
    private final FilteredList<StockMovement> filteredMovements = new FilteredList<>(allMovements);

    private Level activeLevel = Level.ALL;
    private Direction activeDirection = Direction.ALL;
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

        export.getStyleClass().add("stock-secondary");
        export.setDisable(true);
        export.setTooltip(new Tooltip("Export the currently filtered rows as a reorder-friendly CSV.  "
                + SHORTCUT + "E"));
        export.setOnAction(event -> exportCsv());

        refresh.getStyleClass().add("stock-secondary");
        refresh.setOnAction(event -> onOpen());

        adjust.getStyleClass().add("stock-adjust-primary");
        adjust.setOnAction(event -> openAdjustment());
        updateAdjustmentAction(null);

        historyJump.getStyleClass().add("stock-secondary");
        historyJump.setTooltip(new Tooltip("Open the movement trail for the selected product.  "
                + SHORTCUT + "M"));
        historyJump.setOnAction(event -> showWorkspace(Workspace.MOVEMENTS));

        search.getStyleClass().add("stock-search");
        search.setPromptText("Search SKU or product name…");
        search.setAccessibleText("Search stock by product or SKU");
        search.setMinWidth(190);
        HBox.setHgrow(search, Priority.ALWAYS);

        clearSearch.getStyleClass().add("stock-chip-btn");
        clearSearch.setOnAction(event -> search.clear());
        show(clearSearch, false);

        VBox title = new VBox(5,
                label("INVENTORY CONTROL", "stock-kicker"),
                label("Stock Command", "stock-title"),
                label("Live on-hand quantities, reorder thresholds and the full unit movement trail.",
                        "stock-subtitle"));
        title.setMinWidth(0);

        HBox actions = line(historyJump, export, refresh, adjust);
        actions.getStyleClass().add("stock-hero-actions");
        VBox.setVgrow(actions, Priority.NEVER);

        toastTray.setAlignment(Pos.CENTER_RIGHT);
        show(toastTray, false);

        HBox tools = line(search, clearSearch, label(SHORTCUT + "F", "stock-kbd-hint"), spacer(), toastTray);
        tools.setAlignment(Pos.CENTER_LEFT);

        HBox head = line(title, spacer(), actions);
        head.setAlignment(Pos.BOTTOM_LEFT);

        VBox hero = new VBox(16, head, tools);
        hero.getStyleClass().add("stock-hero");

        HBox cards = new HBox(14,
                metric("ACTIVE CATALOG", Level.ALL),
                metric("HEALTHY STOCK", Level.IN_STOCK),
                metric("LOW STOCK", Level.LOW),
                metric("OUT OF STOCK", Level.OUT));
        cards.setMinWidth(0);

        notice.setWrapText(true);
        show(notice, false);

        HBox levelSwitch = segmented(Level.values(), this::setActiveLevel, levelTabs,
                level -> "stock-seg-" + level.style);

        configureStockTable();

        HBox availabilityHead = line(
                new VBox(3, label("Inventory availability", "stock-heading"),
                        label("Every tracked product with its health against the reorder threshold.",
                                "stock-caption")),
                spacer(), resultCount, totalUnitsBadge);
        availabilityHead.setAlignment(Pos.CENTER_LEFT);
        HBox availabilityFilters = line(levelSwitch);

        VBox availability = panel(availabilityHead, availabilityFilters, stockTable);

        configureMovementTable();

        HBox directionSwitch = segmented(Direction.values(), direction -> {
            activeDirection = direction;
            applyMovementDirectionFilter();
        }, directionTabs, direction -> "stock-seg-" + direction.style);

        HBox selectedProduct = line(movementSkuBadge, movementProduct);
        selectedProduct.setAlignment(Pos.CENTER_LEFT);
        selectedProduct.setMinWidth(0);
        HBox.setHgrow(movementProduct, Priority.ALWAYS);

        HBox historyHead = line(
                new VBox(3, label("Movement history", "stock-heading"),
                        label("Signed quantity and value changes for the selected product.",
                                "stock-caption")),
                spacer(), selectedProduct);
        historyHead.setAlignment(Pos.CENTER_LEFT);
        HBox historySummary = line(
                movementStat("EVENTS", movementEventsValue),
                movementStat("INBOUND UNITS", movementInValue),
                movementStat("OUTBOUND UNITS", movementOutValue),
                spacer(), directionSwitch);
        historySummary.setAlignment(Pos.CENTER_LEFT);
        historySummary.getStyleClass().add("stock-movement-summary");

        historyPanel = panel(historyHead, historySummary, movementTable);
        VBox.setVgrow(historyPanel, Priority.ALWAYS);

        availabilityPanel = panel(availabilityHead, availabilityFilters, stockTable);
        VBox.setVgrow(availabilityPanel, Priority.ALWAYS);

        workspaceBody.getChildren().addAll(availabilityPanel, historyPanel);
        workspaceBody.getStyleClass().add("stock-workspace-body");
        VBox.setVgrow(workspaceBody, Priority.ALWAYS);

        HBox workspaceHead = line(
                segmented(Workspace.values(), this::showWorkspace, workspaceTabs, option -> "stock-seg-all"),
                spacer(),
                label("The movement trail follows the product selected in Availability.", "stock-caption"));
        workspaceHead.setAlignment(Pos.CENTER_LEFT);
        workspaceHead.getStyleClass().add("stock-workspace-head");

        VBox workspaceBox = new VBox(12, workspaceHead, workspaceBody);
        workspaceBox.getStyleClass().add("stock-workspace");
        VBox.setVgrow(workspaceBox, Priority.ALWAYS);

        getChildren().addAll(hero, cards, notice, workspaceBox);

        search.textProperty().addListener((property, previous, current) -> {
            show(clearSearch, current != null && !current.isEmpty());
            applyFilters(false);
        });

        stockTable.getSelectionModel().selectedItemProperty().addListener((property, previous, current) -> {
            if (!replacingRows) showMovements(current, false);
        });

        levelTabs.get(Level.ALL).setSelected(true);
        directionTabs.get(Direction.ALL).setSelected(true);
        updateCardSelectionStyles();
        showWorkspace(Workspace.AVAILABILITY);
        addEventHandler(KeyEvent.KEY_PRESSED, this::handleShortcut);
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
        if (adjustedProductId != null) {
            setNotice("Adjustment saved. Updating stock and movement history…", "info");
        } else if (!loaded) {
            stockPlaceholder.setText("Loading stock…");
        }
        if (!loaded) show(notice, false);

        var catalogue = productApi.products().exceptionally(error -> null);
        api.fetchStockAvailability().thenCombine(catalogue, StockLoad::new)
                .whenComplete((data, error) -> Platform.runLater(() -> {
            loading = false;
            refresh.setDisable(false);
            if (error != null) {
                if (!loaded) stockPlaceholder.setText("Stock could not be loaded. Check the backend, then refresh.");
                String prefix = adjustedProductId != null ? "Adjustment saved, but stock couldn’t refresh. "
                        : loaded ? "Couldn’t refresh stock. Showing previous data. " : "Couldn’t load stock. ";
                setNotice(prefix + errorMessage(error), "error");
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
            applyFilters(true);

            if (adjustedProductId != null) toast("Stock adjustment saved");
            if (!adjustmentReady) {
                setNotice("Product details could not be loaded, so stock adjustments are unavailable. Refresh to retry.",
                        "warn");
            } else {
                announceHealth();
            }
        }));
    }

    /** Replaces the type-ahead banner with a short read of the current stock health. */
    private void announceHealth() {
        long out = snapshot.stream().filter(row -> levelOf(row) == Level.OUT).count();
        long low = snapshot.stream().filter(row -> levelOf(row) == Level.LOW).count();
        if (snapshot.isEmpty()) {
            setNotice("No active products yet. Add products to start tracking stock.", "info");
        } else if (out > 0) {
            setNotice(plural(out, "product is", "products are") + " out of stock and "
                    + plural(low, "1 other is", low + " others are") + " at or below threshold. "
                    + "Filter by Out of stock to act first.", "warn");
        } else if (low > 0) {
            setNotice(plural(low, "1 product is", low + " products are") + " at or below the reorder threshold.",
                    "info");
        } else {
            setNotice(plural(snapshot.size(), "All 1 product is", "All " + snapshot.size() + " products are")
                    + " above their reorder threshold.", "ok");
        }
    }

    private static String plural(long count, String one, String many) {
        return count == 1 ? one : many;
    }

    private void openAdjustment() {
        LowStockItem row = stockTable.getSelectionModel().getSelectedItem();
        if (getScene() == null || !canAdjust(row)) {
            toast("Select an active product to adjust");
            return;
        }
        Product product = productById.get(row.productId());
        if (StockDialogs.adjust(getScene().getWindow(), product, row.stockQuantity(), productApi)) {
            reload(row.productId());
        }
    }

    private boolean canAdjust(LowStockItem row) {
        if (row == null || !adjustmentReady || loading) return false;
        Product product = productById.get(row.productId());
        return product != null && "ACTIVE".equals(product.status());
    }

    private void updateAdjustmentAction(LowStockItem row) {
        Product product = row == null ? null : productById.get(row.productId());
        boolean active = product != null && "ACTIVE".equals(product.status());
        show(adjust, row == null || active);
        adjust.setDisable(!adjustmentReady || loading || !active);
        adjust.setTooltip(new Tooltip(row == null ? "Select a product first."
                : !adjustmentReady || product == null ? "Refresh to load current stock and product details."
                : active ? "Add or remove stock for " + product.name() + ".  " + SHORTCUT + "Shift+A"
                : "Only active products can be adjusted."));
    }

    private void restoreAdjustedSelection(long productId) {
        LowStockItem updated = snapshot.stream().filter(row -> row.productId() == productId).findFirst().orElse(null);
        if (updated != null && activeLevel != Level.ALL && levelOf(updated) != activeLevel) {
            // Show the edited row even if it crossed a stock-health threshold.
            activeLevel = Level.ALL;
            levelTabs.get(Level.ALL).setSelected(true);
            updateCardSelectionStyles();
        }
        if (activeDirection != Direction.ALL) {
            activeDirection = Direction.ALL;
            directionTabs.get(Direction.ALL).setSelected(true);
            applyMovementDirectionFilter();
        }
    }

    private void setActiveLevel(Level level) {
        if (syncingFilterControls || activeLevel == level) return;
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
            if (level == activeLevel) card.getStyleClass().add("stock-metric-selected");
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

        TableColumn<LowStockItem, String> skuCol = new TableColumn<>("SKU");
        skuCol.setPrefWidth(132);
        skuCol.setMinWidth(104);
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

        TableColumn<LowStockItem, String> nameCol = new TableColumn<>("Product");
        nameCol.setPrefWidth(300);
        nameCol.setMinWidth(170);
        nameCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().productName()));
        nameCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("stock-cell-strong"); }
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(empty ? null : name == null ? "—" : name);
                setTooltip(name == null || name.length() < 24 ? null : new Tooltip(name));
            }
        });
        stockTable.getColumns().add(nameCol);

        TableColumn<LowStockItem, LowStockItem> onHandCol = new TableColumn<>("On hand");
        onHandCol.setPrefWidth(108);
        onHandCol.setMinWidth(88);
        onHandCol.getStyleClass().add("stock-col-numeric");
        onHandCol.setComparator((a, b) -> Long.compare(a.stockQuantity(), b.stockQuantity()));
        onHandCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        onHandCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LowStockItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("stock-qty-in", "stock-qty-low", "stock-qty-out");
                setAlignment(Pos.CENTER_RIGHT);
                setTooltip(null);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(formatInt(item.stockQuantity()));
                getStyleClass().add("stock-qty-" + levelOf(item).style);
                long reorder = suggestedReorder(item);
                setTooltip(new Tooltip(reorder == 0
                        ? formatInt(item.stockQuantity()) + " units on hand, above the threshold."
                        : formatInt(item.stockQuantity()) + " on hand · suggested reorder +"
                                + formatInt(reorder) + " units"));
            }
        });
        stockTable.getColumns().add(onHandCol);

        TableColumn<LowStockItem, Integer> thresholdCol = new TableColumn<>("Threshold");
        thresholdCol.setPrefWidth(112);
        thresholdCol.setMinWidth(92);
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

        TableColumn<LowStockItem, LowStockItem> health = new TableColumn<>("Stock health");
        health.setPrefWidth(196);
        health.setMinWidth(168);
        health.setComparator((a, b) -> {
            int compared = levelOf(a).compareTo(levelOf(b));
            return compared != 0 ? compared : Long.compare(a.stockQuantity(), b.stockQuantity());
        });
        health.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        health.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LowStockItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (empty || item == null) return;

                Level level = levelOf(item);
                Label badge = pill(level.chipLabel, "stock-badge", level.tone());

                // Visual ratio bar relative to 2x threshold (never narrower than 10 units of scale).
                double reference = Math.max(10.0, item.lowStockThreshold() * 2.0);
                double ratio = item.stockQuantity() <= 0 ? 0.0
                        : Math.min(1.0, Math.max(0.08, item.stockQuantity() / reference));

                Region fill = new Region();
                fill.getStyleClass().addAll("stock-bar-fill", "stock-bar-" + level.style);
                fill.setMinHeight(6);
                fill.setPrefHeight(6);
                fill.setMaxHeight(6);
                fill.setMinWidth(64 * ratio);
                fill.setPrefWidth(64 * ratio);
                fill.setMaxWidth(64 * ratio);

                StackPane track = new StackPane(fill);
                track.setAlignment(Pos.CENTER_LEFT);
                track.getStyleClass().add("stock-bar-track");
                track.setMinSize(64, 6);
                track.setPrefSize(64, 6);
                track.setMaxSize(64, 6);

                HBox box = new HBox(10, track, badge);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });
        stockTable.getColumns().add(health);

        TableColumn<LowStockItem, LowStockItem> reorderCol = new TableColumn<>("Reorder");
        reorderCol.setPrefWidth(108);
        reorderCol.setMinWidth(92);
        reorderCol.getStyleClass().add("stock-col-numeric");
        reorderCol.setComparator((a, b) -> Long.compare(suggestedReorder(a), suggestedReorder(b)));
        reorderCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        reorderCol.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LowStockItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                setAlignment(Pos.CENTER_RIGHT);
                if (empty || item == null) return;
                long reorder = suggestedReorder(item);
                if (reorder == 0) {
                    setText("—");
                    getStyleClass().add("stock-cell-muted-num");
                    return;
                }
                setGraphic(pill("+" + formatInt(reorder), "stock-badge",
                        levelOf(item) == Level.OUT ? "stock-badge-rose" : "stock-badge-amber"));
            }
        });
        stockTable.getColumns().add(reorderCol);

        ContextMenu rowMenu = new ContextMenu();
        rowMenu.getStyleClass().add("stock-context-menu");
        MenuItem viewMovements = new MenuItem("View movement history");
        MenuItem adjustItem = new MenuItem("Adjust stock…");
        MenuItem copySku = new MenuItem("Copy SKU");
        MenuItem copyName = new MenuItem("Copy product name");
        MenuItem copyRow = new MenuItem("Copy row");
        MenuItem filterSku = new MenuItem("Filter to this SKU");
        rowMenu.getItems().addAll(viewMovements, adjustItem, new SeparatorMenuItem(), copySku, copyName,
                copyRow, filterSku);
        viewMovements.setOnAction(event -> showWorkspace(Workspace.MOVEMENTS));
        adjustItem.setOnAction(event -> openAdjustment());
        copySku.setOnAction(event -> copyValue(selectedRow(), LowStockItem::sku, "SKU"));
        copyName.setOnAction(event -> copyValue(selectedRow(), LowStockItem::productName, "Product name"));
        copyRow.setOnAction(event -> {
            LowStockItem row = selectedRow();
            if (row == null) return;
            copyText(row.sku() + "\t" + row.productName() + "\t" + row.stockQuantity() + "\t"
                    + row.lowStockThreshold() + "\t" + levelOf(row).chipLabel, "Row");
        });
        filterSku.setOnAction(event -> {
            LowStockItem row = selectedRow();
            if (row != null && row.sku() != null) search.setText(row.sku());
        });
        rowMenu.setOnShowing(event -> adjustItem.setDisable(!canAdjust(selectedRow())));

        stockTable.setRowFactory(view -> {
            TableRow<LowStockItem> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) return;
                stockTable.getSelectionModel().select(row.getIndex());
                rowMenu.show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openAdjustment();
            });
            return row;
        });
    }

    private void configureMovementTable() {
        movementTable.getStyleClass().add("stock-table");
        movementTable.setAccessibleText("Stock movement history");
        movementTable.setPlaceholder(movementPlaceholder);
        movementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movementTable.setMinHeight(120);

        SortedList<StockMovement> sortedMovements = new SortedList<>(filteredMovements);
        sortedMovements.comparatorProperty().bind(movementTable.comparatorProperty());
        movementTable.setItems(sortedMovements);

        TableColumn<StockMovement, LocalDateTime> date = column("When", 158, StockMovement::movementAt,
                DATE_TIME::format, null, false);
        movementTable.getColumns().add(date);

        TableColumn<StockMovement, String> typeCol = new TableColumn<>("Movement");
        typeCol.setPrefWidth(150);
        typeCol.setMinWidth(122);
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

        TableColumn<StockMovement, Integer> qtyCol = new TableColumn<>("Qty Δ");
        qtyCol.setPrefWidth(94);
        qtyCol.setMinWidth(78);
        qtyCol.getStyleClass().add("stock-col-numeric");
        qtyCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantityChange()));
        qtyCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer qty, boolean empty) {
                super.updateItem(qty, empty);
                setGraphic(null);
                setText(null);
                setAlignment(Pos.CENTER_RIGHT);
                if (empty || qty == null) return;
                String text = qty > 0 ? "+" + formatInt(qty) : formatInt(qty);
                String tone = qty > 0 ? "stock-badge-jade"
                        : qty < 0 ? "stock-badge-rose" : "stock-badge-neutral";
                setGraphic(pill(text, "stock-badge", tone));
            }
        });
        movementTable.getColumns().add(qtyCol);

        TableColumn<StockMovement, BigDecimal> valCol = new TableColumn<>("Value Δ (MAD)");
        valCol.setPrefWidth(132);
        valCol.setMinWidth(108);
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

        TableColumn<StockMovement, String> serialCol = new TableColumn<>("Serial");
        serialCol.setPrefWidth(150);
        serialCol.setMinWidth(118);
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

        TableColumn<StockMovement, StockMovement> detail = new TableColumn<>("Details");
        detail.setPrefWidth(240);
        detail.setSortable(false);
        detail.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        detail.setCellFactory(column -> new TableCell<>() {
            { getStyleClass().add("stock-cell-muted-num"); }
            @Override protected void updateItem(StockMovement item, boolean empty) {
                super.updateItem(item, empty);
                String value = empty || item == null ? null : movementDetails(item);
                setText(value);
                setTooltip(value == null || "—".equals(value) ? null : new Tooltip(value));
            }
        });
        movementTable.getColumns().add(detail);
    }

    private void applyFilters(boolean refreshHistory) {
        if (!loaded) return;

        LowStockItem selected = stockTable.getSelectionModel().getSelectedItem();
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        Level filter = activeLevel;

        var results = snapshot.stream()
                .filter(row -> filter == null || filter == Level.ALL || levelOf(row) == filter)
                .filter(row -> query.isEmpty()
                        || contains(row.sku(), query)
                        || contains(row.productName(), query))
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
                if (sameProduct == null) stockTable.getSelectionModel().selectFirst();
                else stockTable.getSelectionModel().select(sameProduct);
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

        stockPlaceholder.setText(snapshot.isEmpty() ? "No active products to display."
                : query.isEmpty() ? "No products in this health band."
                : "No product matches “" + search.getText().strip() + "”.");
        showMovements(stockTable.getSelectionModel().getSelectedItem(), refreshHistory);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void showMovements(LowStockItem row, boolean forceRefresh) {
        updateAdjustmentAction(row);
        Long productId = row == null ? null : row.productId();
        if (row == null) {
            show(movementSkuBadge, false);
            movementProduct.setText("Select a product above");
            movementProduct.getStyleClass().remove("stock-cell-strong");
        } else {
            movementSkuBadge.setText(row.sku() == null ? "—" : row.sku());
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
        resetMovementStats();

        if (row == null) {
            movementPlaceholder.setText("Select a product in Availability to see its movement trail.");
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
            resetMovementStats();
            return;
        }
        long added = items.stream().mapToLong(m -> Math.max(0, m.quantityChange())).sum();
        long removed = items.stream().mapToLong(m -> Math.max(0L, -(long) m.quantityChange())).sum();
        movementEventsValue.setText(formatInt(items.size()));
        movementInValue.setText("+" + formatInt(added));
        movementOutValue.setText("−" + formatInt(removed));
    }

    private void resetMovementStats() {
        movementEventsValue.setText("—");
        movementInValue.setText("—");
        movementOutValue.setText("—");
    }

    private static VBox movementStat(String caption, Label value) {
        VBox block = new VBox(1, label(caption, "stock-stat-label"), value);
        block.getStyleClass().add("stock-stat");
        block.setAlignment(Pos.CENTER_LEFT);
        return block;
    }

    /** Swaps the workspace between the availability table and the movement trail. */
    private void showWorkspace(Workspace target) {
        workspace = target;
        ToggleButton toggle = workspaceTabs.get(target);
        if (toggle != null && !toggle.isSelected()) toggle.setSelected(true);
        show(availabilityPanel, target == Workspace.AVAILABILITY);
        show(historyPanel, target == Workspace.MOVEMENTS);
        if (target == Workspace.MOVEMENTS) {
            LowStockItem row = selectedRow();
            if (row == null) {
                movementPlaceholder.setText("Select a product in Availability to see its movement trail.");
            } else {
                showMovements(row, false);
                Platform.runLater(movementTable::requestFocus);
            }
        } else {
            stockTable.requestFocus();
        }
    }

    private void applyMovementDirectionFilter() {
        filteredMovements.setPredicate(m -> switch (activeDirection) {
            case ALL -> true;
            case INBOUND -> m.quantityChange() > 0;
            case OUTBOUND -> m.quantityChange() < 0;
        });
        movementPlaceholder.setText(allMovements.isEmpty()
                ? "No stock movements recorded for this product."
                : "No movements in this direction.");
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
        long reorderUnits = snapshot.stream().mapToLong(StockView::suggestedReorder).sum();

        totals.forEach((level, value) -> value.setText(formatInt(counts.getOrDefault(level, 0))));

        subTotals.get(Level.ALL).setText(formatInt(totalUnits) + " units on hand");
        subTotals.get(Level.IN_STOCK).setText(formatInt(healthyUnits) + " units above threshold");
        subTotals.get(Level.LOW).setText(formatInt(lowUnits) + " units nearing depletion");
        subTotals.get(Level.OUT).setText(counts.getOrDefault(Level.OUT, 0) == 0
                ? "No stockouts" : formatInt(reorderUnits) + " units to reorder");
    }

    private void exportCsv() {
        List<LowStockItem> rowsToExport = List.copyOf(stockTable.getItems());
        if (rowsToExport.isEmpty()) {
            toast("Nothing to export in this view");
            return;
        }

        var chooser = new FileChooser();
        chooser.setTitle("Export Stock Availability");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("stock-availability.csv");
        var file = getScene() == null ? null : chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        exporting = true;
        export.setDisable(true);
        Path destination = file.toPath();

        CompletableFuture.runAsync(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write("SKU,Product,On hand,Alert threshold,Status,Suggested reorder\r\n");
                for (LowStockItem item : rowsToExport) {
                    writer.write(csvCell(item.sku()));
                    writer.write(',');
                    writer.write(csvCell(item.productName()));
                    writer.write(',');
                    writer.write(Long.toString(item.stockQuantity()));
                    writer.write(',');
                    writer.write(Integer.toString(item.lowStockThreshold()));
                    writer.write(',');
                    writer.write(csvCell(levelOf(item).chipLabel));
                    writer.write(',');
                    writer.write(Long.toString(suggestedReorder(item)));
                    writer.write("\r\n");
                }
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }).whenComplete((unused, error) -> Platform.runLater(() -> {
            exporting = false;
            export.setDisable(visibleRows.isEmpty());
            if (error == null) {
                toast("Exported " + rowsToExport.size() + " rows to " + file.getName());
            } else {
                setNotice("Couldn’t save the CSV. Choose a writable folder and try again.", "error");
            }
        }));
    }

    private void handleShortcut(KeyEvent event) {
        if (!(event.isControlDown() || event.isMetaDown())) {
            if (event.getCode() == KeyCode.ESCAPE && search.isFocused() && !search.getText().isEmpty()) {
                search.clear();
                event.consume();
            }
            return;
        }
        switch (event.getCode()) {
            case F -> {
                search.requestFocus();
                search.selectAll();
                event.consume();
            }
            case R -> {
                if (!loading) onOpen();
                event.consume();
            }
            case E -> {
                if (!export.isDisabled()) exportCsv();
                event.consume();
            }
            case M -> {
                showWorkspace(Workspace.MOVEMENTS);
                event.consume();
            }
            case A -> {
                if (event.isShiftDown()) {
                    openAdjustment();
                    event.consume();
                }
            }
            default -> { }
        }
    }

    private LowStockItem selectedRow() {
        return stockTable.getSelectionModel().getSelectedItem();
    }

    private void copyValue(LowStockItem row, Function<LowStockItem, String> value, String what) {
        if (row == null) return;
        copyText(value.apply(row), what);
    }

    private void copyText(String text, String what) {
        if (text == null || text.isBlank()) {
            toast("Nothing to copy");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        toast(what + " copied");
    }

    /** Adds a short-lived pill to the hero so confirmations never block the table. */
    private void toast(String message) {
        Label pill = label(message, "stock-toast");
        pill.setOpacity(0);
        toastTray.getChildren().add(pill);
        while (toastTray.getChildren().size() > 3) toastTray.getChildren().remove(0);
        show(toastTray, true);

        FadeTransition appear = new FadeTransition(Duration.millis(170), pill);
        appear.setToValue(1);
        PauseTransition hold = new PauseTransition(Duration.millis(2200));
        FadeTransition fade = new FadeTransition(Duration.millis(260), pill);
        fade.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(appear, hold, fade);
        sequence.setOnFinished(event -> {
            toastTray.getChildren().remove(pill);
            show(toastTray, !toastTray.getChildren().isEmpty());
        });
        sequence.play();
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

    /** Units needed to restore twice the alert threshold. Zero when the product is comfortable. */
    private static long suggestedReorder(LowStockItem row) {
        long target = Math.max(row.lowStockThreshold() * 2L, row.lowStockThreshold() + 1L);
        return Math.max(0, target - row.stockQuantity());
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
        if (movement.reversalOfMovementId() != null) parts.add("Reverses #" + movement.reversalOfMovementId());
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
        if (error instanceof ConnectException) return "The backend is not reachable. Start it, then refresh.";
        if (error instanceof HttpTimeoutException) return "The backend timed out. Refresh to try again.";
        String message = error.getMessage();
        if (error instanceof IllegalStateException && message != null && message.startsWith("GET /api/")) {
            return message + ". Refresh to retry.";
        }
        return "Refresh to try again.";
    }

    private void setNotice(String value, String tone) {
        notice.getStyleClass().removeAll("stock-notice-info", "stock-notice-ok", "stock-notice-warn",
                "stock-notice-error");
        notice.getStyleClass().add("stock-notice-" + tone);
        notice.setText(value == null ? "" : value);
        show(notice, value != null && !value.isBlank());
    }

    private VBox metric(String title, Level level) {
        Label heading = label(title, "stock-metric-label");
        Label value = label("—", "stock-metric-value");
        Label sub = label("Loading…", "stock-metric-sub");
        totals.put(level, value);
        subTotals.put(level, sub);

        Region stripe = new Region();
        stripe.getStyleClass().addAll("stock-metric-stripe", "stock-stripe-" + level.style);
        stripe.setMinHeight(4);
        stripe.setPrefHeight(4);
        stripe.setMaxHeight(4);

        VBox card = new VBox(4, heading, value, sub, stripe);
        card.getStyleClass().addAll("stock-metric", "stock-" + level.style);
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(event -> setActiveLevel(level));
        card.setAccessibleText(title + " filter " + level.label);
        Tooltip.install(card, new Tooltip("Filter the table by " + level.label.toLowerCase(Locale.ROOT)));
        HBox.setHgrow(card, Priority.ALWAYS);
        metricCards.put(level, card);
        return card;
    }

    /** Builds a pill segmented control that keeps one option always selected. */
    private <T extends Enum<T>> HBox segmented(T[] values, java.util.function.Consumer<T> onSelect,
            EnumMap<T, ToggleButton> registry, Function<T, String> extraStyle) {
        HBox group = new HBox(3);
        group.getStyleClass().add("stock-seg-group");
        group.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup toggleGroup = new ToggleGroup();
        for (T option : values) {
            ToggleButton button = new ToggleButton(option.toString());
            button.setToggleGroup(toggleGroup);
            button.getStyleClass().addAll("stock-seg-btn", extraStyle.apply(option));
            button.setOnAction(event -> {
                if (syncingFilterControls) return;
                onSelect.accept(option);
            });
            registry.put(option, button);
            group.getChildren().add(button);
        }
        toggleGroup.selectedToggleProperty().addListener((observed, previous, current) -> {
            if (current == null && previous != null) previous.setSelected(true);
        });
        return group;
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

    private static VBox panel(Node... children) {
        VBox box = new VBox(13, children);
        box.getStyleClass().add("stock-panel");
        box.setMinHeight(160);
        for (Node child : children) {
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

    private static Label label(String value, String... styles) {
        Label label = new Label(value);
        label.getStyleClass().addAll(styles);
        return label;
    }
}
