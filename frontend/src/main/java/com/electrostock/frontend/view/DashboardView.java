package com.electrostock.frontend.view;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.electrostock.frontend.api.InventoryReportClient;
import com.electrostock.frontend.api.InventoryReportClient.LowStockItem;
import com.electrostock.frontend.api.InventoryReportClient.SalesDay;
import com.electrostock.frontend.api.InventoryReportClient.StockActivity;
import com.electrostock.frontend.api.InventoryReportClient.StockItem;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public class DashboardView extends VBox {

    private static final String STOCK_GREEN = "#12A67E";
    private static final String STOCK_RED = "#E2544C";
    private static final String STOCK_AMBER = "#F0A22E";
    private static final String STOCK_VIOLET = "#7C5CFF";
    private static final String ACCENT = "#5D4CE2";
    private static final String INK = "#14162B";
    private static final String MUTED = "#6B7290";
    // Bar colours shared by the sales chart and the product-profit chart.
    private static final String CHART_REVENUE = "#0870F8";
    private static final String CHART_PROFIT = "#43B7A1";
    private static final String BAR_GAIN = CHART_PROFIT;
    private static final String BAR_LOSS = "#F86E67";
    // Amounts printed on the bars are dark so they stay readable on the light bar colours.
    private static final String BAR_LABEL_INK = "#101822";
    private static final String METRIC_ICON = "#008C79";

    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ACTIVITY_TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter HERO_DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);
    private static final PseudoClass ROW_HOVER = PseudoClass.getPseudoClass("row-hover");

    // The opening screen must fit without scrolling, so the lists are short on purpose:
    // the profit chart shows only the top movers and the activity card the latest rows.
    private static final int PROFIT_ROWS = 5;
    private static final double PROFIT_ROW_HEIGHT = 20;
    private static final int ACTIVITY_ROWS = 5;

    // Widest a single sales bar may grow, so one quiet day doesn't become a slab.
    private static final double MAX_SALES_BAR_WIDTH = 15;

    private long salesRequestNumber;

    private final InventoryReportClient inventoryReports = new InventoryReportClient();
    private final List<Runnable> inventoryLoaders = new java.util.ArrayList<>();
    private final IntegerProperty pendingReports = new SimpleIntegerProperty();
    private Label stockCaption;

    //Constructor
    public DashboardView(Runnable onNewSale, Runnable onNewPurchase) {
        getStylesheets().add(
                DashboardView.class.getResource("dashboard.css").toExternalForm());
        getStyleClass().add("dashboard-page");

        Label title = new Label("Dashboard");
        title.getStyleClass().add("hero-title");

        Label live = new Label("Live data");
        live.getStyleClass().add("hero-chip");

        Label today = new Label(LocalDate.now().format(HERO_DATE));
        today.getStyleClass().add("hero-date");

        // One slim row (title, status chip, date) keeps the whole dashboard on a single screen.
        HBox heroCopy = new HBox(12, title, live, today);
        heroCopy.setAlignment(Pos.CENTER_LEFT);

        Button newSale = new Button("New sale");
        newSale.getStyleClass().add("primary-action");
        newSale.setOnAction(event -> onNewSale.run());

        Button newPurchase = new Button("New purchase");
        newPurchase.getStyleClass().add("secondary-action");
        newPurchase.setOnAction(event -> onNewPurchase.run());

        newSale.setGraphic(icon("New sale", "white"));
        newPurchase.setGraphic(icon("New purchase", INK));

        Button refreshAll = new Button("Refresh all", icon("Refresh", INK));
        refreshAll.getStyleClass().add("secondary-action");
        refreshAll.disableProperty().bind(pendingReports.greaterThan(0));

        HBox heroActions = new HBox(9, refreshAll, newSale, newPurchase);
        heroActions.setAlignment(Pos.CENTER_RIGHT);
        heroActions.getStyleClass().add("hero-actions");

        HBox heroRow = new HBox(18, heroCopy, flexibleSpace(), heroActions);
        heroRow.setAlignment(Pos.CENTER_LEFT);

        VBox hero = new VBox(heroRow);
        hero.getStyleClass().add("dashboard-hero");

        ComboBox<String> salesPeriod = new ComboBox<>();
        salesPeriod.getItems().addAll("This month", "Today");
        salesPeriod.setValue("This month");
        salesPeriod.setAccessibleText("Sales period");
        salesPeriod.getStyleClass().add("period-selector");

        VBox salesContent = new VBox(12);
        salesPeriod.setOnAction(event ->
                updateSalesPanel(salesContent, salesPeriod.getValue()));

        VBox salesCard = new VBox(16,
                cardHead("Sales", "Sales performance", "Revenue and gross profit",
                        ACCENT, salesPeriod),
                salesContent);
        salesCard.getStyleClass().add("report-card");
        VBox.setVgrow(salesContent, Priority.ALWAYS);

        VBox stockCard = createStockPanel();
        stockCard.getStyleClass().add("report-card");

        GridPane sections = new GridPane();
        sections.setHgap(16);
        sections.setVgap(16);

        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setFillWidth(true);
            sections.getColumnConstraints().add(column);
        }

        sections.add(salesCard, 0, 0);
        sections.add(stockCard, 1, 0);
        sections.add(createLowStockPanel(), 0, 1);
        sections.add(createRecentActivityPanel(), 1, 1);

        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(0);
        // Everything fits on opening; the slim scrollbar only appears if the window is made smaller.
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("dashboard-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(hero, scroll);
        refreshAll.setOnAction(event -> {
            updateSalesPanel(salesContent, salesPeriod.getValue());
            inventoryLoaders.forEach(Runnable::run);
        });

        Runnable syncChip = () -> {
            boolean busy = pendingReports.get() > 0;
            live.setText(busy ? "Syncing…" : "Live data");
            live.getStyleClass().remove("is-syncing");
            if (busy) live.getStyleClass().add("is-syncing");
        };
        pendingReports.addListener((obs, oldValue, value) -> syncChip.run());
        refreshAll.fire();
    }

    /** One bar of the sales chart; losses turn red and round on the side facing away from zero. */
    private XYChart.Data<String, Number> salesPoint(
            String label, BigDecimal amount, String color, String caption) {
        XYChart.Data<String, Number> point = new XYChart.Data<>(label, amount);
        boolean negative = amount.signum() < 0;
        String fill = negative ? BAR_LOSS : color;

        StackPane bar = new StackPane();
        bar.setStyle("-fx-bar-fill: " + fill + "; -fx-background-color: " + fill
                + "; -fx-background-radius: " + (negative ? "0 0 5 5" : "5 5 0 0") + ";");

        String detail = caption + String.format(Locale.ENGLISH, "%,.2f MAD", amount);
        Tooltip.install(bar, tip(detail));
        bar.setAccessibleText(detail);
        point.setNode(bar);
        return point;
    }

    private static Tooltip tip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(javafx.util.Duration.millis(120));
        return tooltip;
    }

    /** Axis labels such as 12.5k or 1.2M, so long amounts never crowd the plot. */
    private static javafx.util.StringConverter<Number> compactAmounts() {
        java.text.DecimalFormat format = new java.text.DecimalFormat("0.###",
                java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return new javafx.util.StringConverter<Number>() {
            @Override public String toString(Number number) {
                double value = number.doubleValue();
                double size = Math.abs(value);
                if (size >= 1e12) return format.format(value / 1e12) + "T";
                if (size >= 1e9) return format.format(value / 1e9) + "B";
                if (size >= 1e6) return format.format(value / 1e6) + "M";
                if (size >= 1000) return format.format(value / 1000) + "k";
                return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }
            @Override public Number fromString(String text) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private Label movementBadge(String type) {
        String word = type.toLowerCase(Locale.ROOT).replace('_', ' ');
        Label badge = new Label(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        badge.getStyleClass().addAll("movement-badge", switch (type) {
            case "SALE" -> "movement-sale";
            case "PURCHASE" -> "movement-purchase";
            case "REVERSAL" -> "movement-reversal";
            default -> "movement-other";
        });
        badge.setMinWidth(78);
        badge.setAlignment(Pos.CENTER);
        return badge;
    }

    private StackPane icon(String name, String color) {
        String path = switch (name) {
            case "Refresh" -> "M20 9A8 8 0 1 0 20 15 M20 3V9H14";
            case "Dashboard" -> "M3 3H9V9H3Z M14 3H20V9H14Z M3 14H9V20H3Z M14 14H20V20H14Z";
            case "Gross profit" -> "M3 7C3 3 19 3 19 7C19 11 3 11 3 7Z M3 7V16C3 21 19 21 19 16V7 M3 12C3 16 19 16 19 12";
            case "Stock at cost", "Units sold", "Products" -> "M2 7L12 2L22 7L12 12Z M2 7V17L12 22L22 17V7 M12 12V22";
            case "Potential sales" -> "M3 12L12 3H21V12L12 21Z M16 7H17";
            case "Purchases", "New sale" -> "M2 3H5L8 15H18L21 7H6 M9 19H11 M16 19H18";
            case "New purchase" -> "M2 5H14V16H2Z M14 9H18L22 13V16H14 M5 18H8 M16 18H19";
            case "Sales" -> "M4 8H20L21 21H3Z M8 9V7A4 4 0 0 1 16 7V9";
            case "Suppliers" -> "M8 11A4 4 0 1 0 8 3A4 4 0 1 0 8 11 M2 21V18C2 12 14 12 14 18V21Z M16 4C21 5 21 11 17 12 M17 15C20 15 22 17 22 21";
            case "Stock" -> "M2 7L12 2L22 7L12 12Z M2 12L12 17L22 12 M2 17L12 22L22 17";
            case "Activity" -> "M12 3A9 9 0 1 0 12 21A9 9 0 1 0 12 3 M12 7V12L16 14";
            default -> "M3 20V13H6V20Z M10 20V8H13V20Z M17 20V3H20V20Z";
        };
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(Color.web(color));
        shape.setStrokeWidth(1.8);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        StackPane box = new StackPane(shape);
        box.setMinSize(23, 23);
        box.setPrefSize(23, 23);
        box.setMaxSize(23, 23);
        box.setMouseTransparent(true);
        return box;
    }

    private Region flexibleSpace() {
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        return space;
    }

    /** Card top row: tinted icon tile, title + caption, optional control on the right. */
    private HBox cardHead(String iconName, String title, String caption,
            String color, Node control) {
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-heading");

        Label sub = new Label(caption);
        sub.getStyleClass().add("card-sub");

        VBox copy = new VBox(2, heading, sub);
        StackPane tile = new StackPane(icon(iconName, color));
        tile.getStyleClass().add("card-icon");

        HBox row = new HBox(11, tile, copy, flexibleSpace());
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(38);
        row.getStyleClass().add("card-head");
        if (control != null) row.getChildren().add(control);
        return row;
    }

    private void styleTableHeader(GridPane table) {
        for (javafx.scene.Node cell : table.getChildren()) {
            Integer row = GridPane.getRowIndex(cell);

            if ((row == null || row == 0) && cell instanceof Label label) {
                label.getStyleClass().add("report-column-heading");
                label.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }

    private VBox createRecentActivityPanel() {
        VBox content = new VBox();

        VBox panel = new VBox(12,
                cardHead("Activity", "Recent stock activity", "Latest movements across products",
                        ACCENT, null),
                content);
        panel.getStyleClass().addAll("report-card", "recent-stock-activity");

        inventoryLoaders.add(() -> {
            pendingReports.set(pendingReports.get() + 1);
            content.getChildren().setAll(emptyState("Loading recent activity…"));

            inventoryReports.fetchRecentActivity().whenComplete((activities, error) ->
                    Platform.runLater(() -> {
                        pendingReports.set(pendingReports.get() - 1);

                        if (error != null) {
                            content.getChildren().setAll(emptyState(
                                    "Couldn’t load activity. Check the backend and refresh."));
                        } else if (activities.isEmpty()) {
                            content.getChildren().setAll(
                                    emptyState("No stock movements recorded yet."));
                        } else {
                            content.getChildren().setAll(createActivityTable(activities));
                        }
                    }));
        });

        return panel;
    }

    private GridPane createActivityTable(List<StockActivity> activities) {
        GridPane table = new GridPane();
        table.getStyleClass().add("stock-activity-table");
        table.setMaxWidth(Double.MAX_VALUE);

        // Columns: Time | Product (takes the leftover width) | Movement | Qty
        ColumnConstraints timeColumn = new ColumnConstraints(104, 112, Region.USE_PREF_SIZE);
        ColumnConstraints productColumn = new ColumnConstraints(90, 190, Double.MAX_VALUE);
        productColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints movementColumn = new ColumnConstraints(96, 104, Region.USE_PREF_SIZE);
        ColumnConstraints quantityColumn = new ColumnConstraints(50, 56, Region.USE_PREF_SIZE);
        table.getColumnConstraints().addAll(
                timeColumn, productColumn, movementColumn, quantityColumn);

        // One purchase of ten units arrives as ten single-unit movements; merge them so a
        // single row reads "+10" instead of ten "+1" lines.
        // Sort newest first here instead of trusting the order the backend returns.
        List<StockActivity> newestFirst = new ArrayList<>(activities);
        newestFirst.sort(Comparator.comparing(StockActivity::movementAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<StockActivity> rows = mergeActivityRows(newestFirst);
        if (rows.size() > ACTIVITY_ROWS) rows = rows.subList(0, ACTIVITY_ROWS);

        addActivityRow(table, 0, rows.size(),
                new Label("Time"), new Label("Product"),
                new Label("Movement"), new Label("Qty"));

        int row = 1;
        for (StockActivity activity : rows) {
            Label quantity = new Label("%+d".formatted(activity.quantityChange()));
            quantity.getStyleClass().add("stock-activity-quantity");
            if (activity.quantityChange() > 0) quantity.getStyleClass().add("gain");
            if (activity.quantityChange() < 0) quantity.getStyleClass().add("loss");

            // Show "SKU – name" in one column so the name has room to breathe.
            String name = activity.productName();
            String text = name == null || name.isBlank()
                    ? activity.sku() : activity.sku() + " – " + name;
            Label product = new Label(text);
            product.getStyleClass().add("stock-activity-product");
            product.setTextOverrun(OverrunStyle.ELLIPSIS);
            product.setTooltip(new Tooltip(text));

            Label time = new Label(ACTIVITY_TIME.format(activity.movementAt()));
            time.getStyleClass().add("stock-activity-time");

            addActivityRow(table, row++, rows.size(),
                    time, product, movementBadge(activity.movementType()), quantity);
        }
        return table;
    }

    /** Movements recorded this close together belong to the same purchase/sale operation. */
    private static final long ACTIVITY_MERGE_WINDOW_SECONDS = 5;

    /**
     * Collapses neighbouring movements of the same product and type that happened together.
     * A purchase of ten serialised units lands as ten "+1" rows; this turns them into "+10".
     */
    private static List<StockActivity> mergeActivityRows(List<StockActivity> activities) {
        List<StockActivity> rows = new ArrayList<>();
        StockActivity open = null;   // row that may still absorb the next movement
        LocalDateTime openAt = null; // time of the newest movement folded into it
        for (StockActivity activity : activities) {
            if (open != null
                    && Objects.equals(open.movementType(), activity.movementType())
                    && Objects.equals(open.sku(), activity.sku())
                    && sameOperation(openAt, activity.movementAt())) {
                open = new StockActivity(
                        open.movementType(),
                        open.sku(),
                        open.productName(),
                        open.quantityChange() + activity.quantityChange(),
                        open.movementAt());
                rows.set(rows.size() - 1, open);
                openAt = activity.movementAt();
            } else {
                open = activity;
                openAt = activity.movementAt();
                rows.add(activity);
            }
        }
        return rows;
    }

    private static boolean sameOperation(LocalDateTime first, LocalDateTime next) {
        if (first == null || next == null) {
            return false;
        }
        return Math.abs(Duration.between(first, next).getSeconds()) <= ACTIVITY_MERGE_WINDOW_SECONDS;
    }

    private void addActivityRow(GridPane table, int row, int lastRow, Label... labels) {
        java.util.List<StackPane> cells = new java.util.ArrayList<>();
        for (int column = 0; column < labels.length; column++) {
            Label label = labels[column];
            // Only the text columns may shrink; time, badge and quantity keep their width.
            if (row == 0 || column == 1) label.setMinWidth(0);
            else label.setMinWidth(Region.USE_PREF_SIZE);
            StackPane cell = new StackPane(label);
            cell.setAlignment(column == labels.length - 1 ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            cell.getStyleClass().add("stock-activity-cell");
            if (row == 0) cell.getStyleClass().add("stock-activity-heading");
            if (row == lastRow) cell.getStyleClass().add("stock-activity-last-row");
            if (column == labels.length - 1) cell.getStyleClass().add("stock-activity-last-column");
            cells.add(cell);
            table.add(cell, column, row);
        }
        if (row != 0) {
            // GridPane has no row hover, so light up every cell of the hovered row.
            for (StackPane cell : cells) {
                cell.hoverProperty().addListener((obs, was, now) ->
                        cells.forEach(c -> c.pseudoClassStateChanged(ROW_HOVER, now)));
            }
        }
    }

    private Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        return label;
    }

    private VBox createLowStockPanel() {
        VBox content = new VBox();

        VBox panel = new VBox(12,
                cardHead("Stock", "Stock availability", "Products by stock health",
                        ACCENT, null),
                content);
        // This card answers "how much can we still sell?", so it is highlighted.
        panel.getStyleClass().addAll("report-card", "stock-availability-card");

        inventoryLoaders.add(() -> {
            pendingReports.set(pendingReports.get() + 1);
            content.getChildren().setAll(emptyState("Loading stock…"));

            inventoryReports.fetchStockAvailability().whenComplete((items, error) ->
                    Platform.runLater(() -> {
                        pendingReports.set(pendingReports.get() - 1);

                        if (error != null) {
                            content.getChildren().setAll(emptyState(
                                    "Couldn’t load stock. Check the backend and refresh."));
                            return;
                        }

                        content.getChildren().setAll(createAvailabilityContent(items));
                    }));
        });

        return panel;
    }

    private VBox createAvailabilityContent(List<LowStockItem> items) {
        int out = 0;
        int low = 0;
        for (LowStockItem item : items) {
            if (item.stockQuantity() <= 0) {
                out++;
            } else if (item.stockQuantity() <= item.lowStockThreshold()) {
                low++;
            }
        }
        int total = items.size();
        int healthy = total - low - out;

        Canvas donut = availabilityDonut(healthy, low, out);

        GridPane cards = new GridPane();
        cards.setHgap(10);
        cards.setVgap(10);
        cards.getStyleClass().add("availability-grid");
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            cards.getColumnConstraints().add(column);
        }
        cards.add(stockCount("Total products", total, -1, "accent"), 0, 0);
        cards.add(stockCount("In stock", healthy, total, "jade"), 1, 0);
        cards.add(stockCount("Low stock", low, total, "amber"), 0, 1);
        cards.add(stockCount("Out of stock", out, total, "rose"), 1, 1);
        HBox.setHgrow(cards, Priority.ALWAYS);
        HBox body = new HBox(20, donut, cards);
        body.setAlignment(Pos.CENTER_LEFT);
        return new VBox(body);
    }

    private Canvas availabilityDonut(int healthy, int low, int out) {
        Canvas ring = new Canvas(184, 184);
        GraphicsContext pen = ring.getGraphicsContext2D();
        double center = ring.getWidth() / 2;
        double outer = center - 3;
        double inner = 54;
        double mid = (inner + outer) / 2;
        int[] counts = {healthy, low, out};
        String[] colors = {STOCK_GREEN, STOCK_AMBER, STOCK_RED};
        int total = healthy + low + out;
        pen.setLineJoin(StrokeLineJoin.ROUND);
        pen.setTextAlign(TextAlignment.CENTER);
        pen.setTextBaseline(VPos.CENTER);

        if (total == 0) {
            pen.setStroke(Color.web("#EEF0F8"));
            pen.setLineWidth(outer - inner);
            pen.strokeOval(center - mid, center - mid, 2 * mid, 2 * mid);
        }

        double start = 90;
        for (int i = 0; i < counts.length; i++) {
            int n = counts[i];
            if (n == 0) continue;
            double sweep = 360.0 * n / total;
            Color color = Color.web(colors[i]);
            pen.setFill(color);
            pen.setStroke(color);
            if (n == total) {
                pen.setLineWidth(outer - inner);
                pen.strokeOval(center - mid, center - mid, 2 * mid, 2 * mid);
            } else {
                // Stroke an inset sector with round joins to soften only its four corners.
                double corner = Math.min(3.5, inner * Math.toRadians(sweep) / 10);
                double trim = Math.min(sweep / 4, Math.toDegrees((corner + 2) / inner));
                pen.setLineWidth(2 * corner);
                pen.beginPath();
                pen.arc(center, center, outer - corner, outer - corner,
                        start - trim, -(sweep - 2 * trim));
                pen.arc(center, center, inner + corner, inner + corner,
                        start - sweep + trim, sweep - 2 * trim);
                pen.closePath();
                pen.fill();
                pen.stroke();
            }
            if (sweep >= 36) {
                double angle = Math.toRadians(start - sweep / 2);
                pen.setFill(Color.WHITE);
                pen.setFont(Font.font("System", FontWeight.BOLD, 11.5));
                pen.fillText(percentLabel(n, total), center + mid * Math.cos(angle),
                        center - mid * Math.sin(angle), outer - inner - 2);
            }
            start -= sweep;
        }

        // Centre readout: total products, with the ring colours as the legend.
        pen.setFill(Color.web(INK));
        pen.setFont(Font.font("System", FontWeight.BOLD, 31));
        pen.fillText(String.valueOf(total), center, center - 9);
        pen.setFill(Color.web(MUTED));
        pen.setFont(Font.font("System", FontWeight.BOLD, 9.5));
        pen.fillText(total == 1 ? "PRODUCT" : "PRODUCTS", center, center + 15);

        ring.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        ring.setAccessibleText("Stock availability. In stock " + healthy
                + ", low stock " + low + ", out of stock " + out + ".");
        return ring;
    }

    private String percentLabel(int count, int total) {
        if (count == 0 || total == 0) return "0%";
        double percent = 100.0 * count / total;
        if (percent < 0.1) return "<0.1%";
        return new java.text.DecimalFormat("0.#",
                java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(percent) + "%";
    }

    private VBox stockCount(String title, int count, int total, String tone) {
        Label caption = new Label(title);
        caption.getStyleClass().add("stock-count-caption");
        Label number = new Label(String.valueOf(count));
        number.getStyleClass().add("stock-count-number");
        HBox values = new HBox(8, number);
        values.setAlignment(Pos.BASELINE_LEFT);
        if (total >= 0) {
            Label percent = new Label(percentLabel(count, total));
            percent.getStyleClass().add("stock-count-percent");
            values.getChildren().add(percent);
        }
        VBox card = new VBox(3, caption, values);
        card.getStyleClass().addAll("stock-count-card", "tone-" + tone);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private String toneColor(String tone) {
        return switch (tone) {
            case "jade" -> STOCK_GREEN;
            case "amber" -> STOCK_AMBER;
            case "rose" -> STOCK_RED;
            case "violet" -> STOCK_VIOLET;
            default -> ACCENT;
        };
    }

    private HBox createMetricCard(String title, String value, boolean negative, String tone) {
        Label caption = new Label(title);
        caption.getStyleClass().add("metric-caption");
        caption.setMinWidth(Region.USE_PREF_SIZE);

        // "58,937.50 MAD" -> amount + unit, shown at the same size.
        String unit = null;
        if (value.endsWith(" MAD")) {
            unit = "MAD";
            value = value.substring(0, value.length() - 4);
        }
        Label amount = new Label(value);
        amount.getStyleClass().add("metric-value");
        if (negative) amount.getStyleClass().add("negative");
        amount.setMinWidth(Region.USE_PREF_SIZE);

        HBox valueRow = new HBox(5, amount);
        valueRow.setAlignment(Pos.BASELINE_LEFT);
        if (unit != null) {
            Label unitLabel = new Label(unit);
            unitLabel.getStyleClass().add("metric-unit");
            unitLabel.setMinWidth(Region.USE_PREF_SIZE);
            valueRow.getChildren().add(unitLabel);
        }

        VBox text = new VBox(2, caption, valueRow);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox card = new HBox(10, icon(title, METRIC_ICON), text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("metric-card", "tone-" + tone);
        card.setPrefWidth(150);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void updateSalesPanel(VBox panel, String period) {
        LocalDate today = LocalDate.now();
        LocalDate from = "Today".equals(period) ? today : today.withDayOfMonth(1);
        long requestNumber = ++salesRequestNumber;
        pendingReports.set(pendingReports.get() + 1);

        panel.getChildren().setAll(emptyState("Loading sales…"));

        inventoryReports.fetchDailySales(from, today).whenComplete((days, error) ->
                Platform.runLater(() -> {
                    pendingReports.set(pendingReports.get() - 1);
                    // A newer period selection may have finished loading first.
                    if (requestNumber != salesRequestNumber) {
                        return;
                    }

                    if (error != null) {
                        Button retry = new Button("Retry");
                        retry.getStyleClass().add("quiet-action");
                        retry.setOnAction(event -> updateSalesPanel(panel, period));
                        panel.getChildren().setAll(
                                emptyState("Couldn’t load sales."), retry);
                        return;
                    }

                    panel.getChildren().setAll(createSalesSummary(days));
                    if (days.isEmpty()) {
                        panel.getChildren().add(
                                emptyState("No confirmed sales in this period."));
                    } else {
                        BarChart<String, Number> chart = createSalesChart(days);
                        VBox.setVgrow(chart, Priority.ALWAYS);
                        panel.getChildren().add(chart);
                    }
                }));
    }

    private HBox createSalesSummary(List<SalesDay> days) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        long units = 0;

        for (SalesDay day : days) {
            revenue = revenue.add(day.revenueMad());
            profit = profit.add(day.profitMad());
            units += day.unitsSold();
        }

        return new HBox(10,
                createMetricCard("Revenue", "%,.2f MAD".formatted(revenue), false, "accent"),
                createMetricCard("Gross profit", "%,.2f MAD".formatted(profit),
                        profit.signum() < 0, profit.signum() < 0 ? "rose" : "jade"),
                createMetricCard("Units sold", String.valueOf(units), false, "violet"));
    }

    private BarChart<String, Number> createSalesChart(List<SalesDay> selectedDays) {
        // A busy month shows just the day number; the month moves to the axis title.
        boolean oneMonth = selectedDays.stream()
                .map(day -> YearMonth.from(day.date())).distinct().count() == 1;
        boolean dense = oneMonth && selectedDays.size() > 8;

        CategoryAxis days = new CategoryAxis();
        days.setTickLabelFill(Color.web(MUTED));
        days.setTickLabelFont(Font.font("System", FontWeight.NORMAL, 11.5));
        days.setTickLabelRotation(0);
        if (dense) days.setLabel(MONTH_LABEL.format(selectedDays.get(0).date()));

        NumberAxis amounts = new NumberAxis();
        amounts.setTickLabelFill(Color.web(MUTED));
        amounts.setTickLabelFont(Font.font("System", FontWeight.NORMAL, 11.5));
        amounts.setTickLabelFormatter(compactAmounts());
        amounts.setMinorTickVisible(false);
        amounts.setLabel("Amount (MAD)");

        BarChart<String, Number> chart = new BarChart<>(days, amounts);
        chart.setPrefHeight(250);
        chart.setMinHeight(220);
        chart.getStyleClass().add("sales-chart");
        chart.setAnimated(false);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(false);
        chart.setLegendSide(Side.BOTTOM);
        chart.setBarGap(5);

        double baseGap = dense ? 6 : 14;
        chart.setCategoryGap(baseGap);
        int categories = selectedDays.size();
        days.widthProperty().addListener((obs, was, width) -> {
            // Widen the gap between days until each bar is at most MAX_SALES_BAR_WIDTH wide.
            double slot = width.doubleValue() / categories;
            chart.setCategoryGap(Math.max(baseGap,
                    slot - 2 * MAX_SALES_BAR_WIDTH - chart.getBarGap()));
        });

        XYChart.Series<String, Number> revenue = new XYChart.Series<>();
        revenue.setName("Revenue (MAD)");

        XYChart.Series<String, Number> profit = new XYChart.Series<>();
        profit.setName("Gross profit (MAD)");

        for (SalesDay day : selectedDays) {
            String full = day.date().format(DAY_LABEL);
            String label = dense ? String.valueOf(day.date().getDayOfMonth()) : full;
            revenue.getData().add(salesPoint(label, day.revenueMad(),
                    CHART_REVENUE, full + " · Revenue: "));
            profit.getData().add(salesPoint(label, day.profitMad(),
                    CHART_PROFIT, full + " · Gross profit: "));
        }

        chart.getData().add(revenue);
        chart.getData().add(profit);
        return chart;
    }

    private VBox createStockPanel() {
        VBox content = new VBox(14);

        ToggleButton overview = new ToggleButton("Overview");
        ToggleButton gains = new ToggleButton("Largest gains");
        ToggleButton losses = new ToggleButton("Largest losses");

        ToggleGroup stockFilter = new ToggleGroup();
        List<ToggleButton> buttons = List.of(overview, gains, losses);
        for (ToggleButton button : buttons) {
            button.setToggleGroup(stockFilter);
            button.getStyleClass().add("segment-button");
        }
        overview.setSelected(true);

        HBox filterBar = new HBox(2, overview, gains, losses);
        filterBar.getStyleClass().add("segment-bar");
        filterBar.setMaxWidth(Region.USE_PREF_SIZE);
        VBox stockRows = new VBox(6);

        HBox head = cardHead("Products", "Stock on hand now", "Potential profit by product",
                ACCENT, filterBar);
        stockCaption = (Label) head.lookup(".card-sub");
        VBox panel = new VBox(16, head, content);

        inventoryLoaders.add(() -> {
            pendingReports.set(pendingReports.get() + 1);
            content.getChildren().setAll(emptyState("Loading inventory…"));

            inventoryReports.fetchInventoryValuation().whenComplete((items, error) ->
                    Platform.runLater(() -> {
                        pendingReports.set(pendingReports.get() - 1);

                        if (error != null) {
                            content.getChildren().setAll(emptyState(
                                    "Couldn’t load inventory. Check the backend and refresh."));
                            return;
                        }

                        Runnable render = () -> {
                            if (overview.isSelected()) {
                                showProfitOverview(stockRows, items);
                            } else {
                                updateStockProfitRows(stockRows, items, losses.isSelected());
                            }
                        };

                        for (ToggleButton button : buttons) {
                            button.setOnAction(e -> {
                                // Clicking the active button keeps that view selected.
                                button.setSelected(true);
                                render.run();
                            });
                        }

                        render.run();
                        content.getChildren().setAll(createStockSummary(items), stockRows);
                    }));
        });

        return panel;
    }

    private HBox createStockSummary(List<StockItem> items) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal potentialSales = BigDecimal.ZERO;

        for (StockItem item : items) {
            totalCost = totalCost.add(item.stockValueMad());
            potentialSales = potentialSales.add(item.potentialSalesMad());
        }

        BigDecimal potentialProfit = potentialSales.subtract(totalCost);

        return new HBox(10,
                createMetricCard("Stock at cost", "%,.2f MAD".formatted(totalCost),
                        false, "accent"),
                createMetricCard("Potential sales", "%,.2f MAD".formatted(potentialSales),
                        false, "violet"),
                createMetricCard("Potential profit", "%+,.2f MAD".formatted(potentialProfit),
                        potentialProfit.signum() < 0,
                        potentialProfit.signum() < 0 ? "rose" : "jade"));
    }

    private void setStockCaption(String text) {
        if (stockCaption != null) stockCaption.setText(text);
    }

    private void showProfitOverview(VBox rows, List<StockItem> items) {
        List<StockItem> withImpact = items.stream()
                .filter(item -> item.potentialProfitMad().signum() != 0)
                .toList();

        if (withImpact.isEmpty()) {
            setStockCaption("Potential profit by product");
            rows.getChildren().setAll(
                    emptyState("No products with a potential gain or loss."));
            return;
        }

        // The biggest movers by absolute impact, shown gains first.
        List<StockItem> movers = withImpact.stream()
                .sorted(Comparator.comparing((StockItem item) -> item.potentialProfitMad().abs())
                        .reversed().thenComparing(StockItem::sku))
                .limit(PROFIT_ROWS)
                .sorted(Comparator.comparing(StockItem::potentialProfitMad)
                        .reversed().thenComparing(StockItem::sku))
                .toList();

        setStockCaption(withImpact.size() <= PROFIT_ROWS
                ? "Potential profit by product"
                : "Top " + movers.size() + " of " + withImpact.size() + " products");
        rows.getChildren().setAll(createProfitChart(movers));
    }

    private void updateStockProfitRows(
            VBox rows, List<StockItem> items, boolean losses) {

        Comparator<StockItem> order =
                Comparator.comparing(StockItem::potentialProfitMad);

        List<StockItem> ranked = items.stream()
                .filter(item -> item.potentialProfitMad().signum() == (losses ? -1 : 1))
                .sorted((losses ? order : order.reversed()).thenComparing(StockItem::sku))
                .toList();

        if (ranked.isEmpty()) {
            setStockCaption("Potential profit by product");
            rows.getChildren().setAll(emptyState(losses
                    ? "No products with a potential loss."
                    : "No products with a potential gain."));
            return;
        }

        List<StockItem> products = ranked.stream().limit(PROFIT_ROWS).toList();
        String kind = losses ? "losses" : "gains";
        setStockCaption(ranked.size() <= PROFIT_ROWS
                ? "Potential " + kind + " by product"
                : "Top " + products.size() + " of " + ranked.size() + " " + kind);
        rows.getChildren().setAll(createProfitChart(products));
    }

    private Region createProfitChart(List<StockItem> products) {
        return createProfitChart(products, products);
    }

    private Region createProfitChart(List<StockItem> products, List<StockItem> scaleProducts) {
        boolean compact = products.size() > 5;
        NumberAxis amounts = new NumberAxis();
        amounts.setTickLabelFill(Color.web(MUTED));
        amounts.setTickLabelFont(Font.font("System", FontWeight.NORMAL, 11.5));
        amounts.setLabel("Potential profit (MAD)");
        amounts.setForceZeroInRange(true);
        amounts.setMinorTickVisible(false);

        double min = Math.min(0, scaleProducts.stream().map(StockItem::potentialProfitMad)
                .min(BigDecimal::compareTo).orElseThrow().doubleValue());
        double max = Math.max(0, scaleProducts.stream().map(StockItem::potentialProfitMad)
                .max(BigDecimal::compareTo).orElseThrow().doubleValue());
        amounts.setAutoRanging(false);
        amounts.setTickLabelRotation(0);
        amounts.setTickLabelFormatter(compactAmounts());
        Runnable scale = () -> {
            int intervals = Math.max(2, Math.min(8, (int) (amounts.getWidth() / 78)));
            double raw = (max - min) / intervals;
            double base = Math.pow(10, Math.floor(Math.log10(raw)));
            double unit = base * (raw <= base ? 1 : raw <= 2 * base ? 2 : raw <= 5 * base ? 5 : 10);
            amounts.setTickUnit(unit);
            amounts.setLowerBound(Math.floor(min / unit) * unit);
            amounts.setUpperBound(Math.ceil(max / unit) * unit);
        };
        scale.run();
        amounts.widthProperty().addListener((obs, oldWidth, width) -> scale.run());

        CategoryAxis skus = new CategoryAxis();
        skus.setTickLabelFill(Color.web(MUTED));
        skus.setTickLabelFont(Font.font("System", FontWeight.BOLD, compact ? 11.5 : 12.5));

        // JavaFX draws the first vertical category at the bottom.
        // Reverse the axis categories to show the first ranked product at the top.
        var categories = FXCollections.<String>observableArrayList();
        for (int i = products.size() - 1; i >= 0; i--) {
            categories.add(products.get(i).sku());
        }
        skus.setCategories(categories);

        BarChart<Number, String> chart = new BarChart<>(amounts, skus);
        chart.getStyleClass().add("product-profit-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);
        chart.setCategoryGap(compact ? 3 : 5);
        chart.setBarGap(0);

        XYChart.Series<Number, String> series = new XYChart.Series<>();

        for (StockItem product : products) {
            BigDecimal profit = product.potentialProfitMad();
            boolean loss = profit.signum() < 0;
            String formatted = String.format(Locale.ENGLISH, "%+,.2f", profit);

            Label value = new Label(formatted);
            value.getStyleClass().add("profit-bar-value");
            value.setMouseTransparent(true);
            value.setMinWidth(Region.USE_PREF_SIZE);
            value.setMaxWidth(Region.USE_PREF_SIZE);

            // Gains round on their right end, losses on their left (the end away from zero).
            String barFill = loss ? BAR_LOSS : BAR_GAIN;
            StackPane bar = new StackPane(value);
            bar.setAlignment(Pos.CENTER);
            bar.setStyle("-fx-bar-fill: " + barFill + "; -fx-background-color: " + barFill
                    + "; -fx-background-radius: " + (loss ? "6 0 0 6" : "0 6 6 0") + ";");

            // The amount sits inside the bar's far end when it fits, otherwise just outside it.
            // It is dark either way, which reads on both the bar and the white card.
            String textStyle = "-fx-font-size: " + (compact ? 11.5 : 12.5)
                    + "px; -fx-font-weight: bold; -fx-text-fill: ";
            String outsideInk = BAR_LABEL_INK;
            boolean[] inside = {true};
            value.setStyle(textStyle + BAR_LABEL_INK + ";");
            Runnable place = () -> {
                double labelWidth = value.prefWidth(-1);
                double barWidth = bar.getWidth();
                double side = loss ? -1 : 1;
                boolean fits = barWidth >= labelWidth + 12;
                value.setTranslateX(side * (fits
                        ? barWidth / 2 - labelWidth / 2 - 6
                        : barWidth / 2 + labelWidth / 2 + 6));
                if (fits != inside[0]) { // restyle only when it flips, never on every layout pass
                    inside[0] = fits;
                    value.setStyle(textStyle + (fits ? BAR_LABEL_INK : outsideInk) + ";");
                }
            };
            bar.widthProperty().addListener((obs, was, now) -> place.run());
            value.widthProperty().addListener((obs, was, now) -> place.run());

            String detail = product.sku() + " · " + formatted + " MAD";
            Tooltip.install(bar, tip(detail));
            bar.setAccessibleText(detail);

            XYChart.Data<Number, String> point =
                    new XYChart.Data<>(profit, product.sku());
            point.setNode(bar);
            series.getData().add(point);
        }

        chart.getData().add(series);

        // Only a handful of bars are drawn, so the chart (MAD axis included) has a fixed
        // height and never needs a scrollbar of its own.
        double height = 78 + PROFIT_ROW_HEIGHT * products.size();
        chart.setMinHeight(height);
        chart.setPrefHeight(height);
        return chart;
    }
}