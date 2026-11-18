package com.electrostock.frontend.view;

import java.math.BigDecimal;

import java.time.LocalDate;

import java.time.format.DateTimeFormatter;

import java.util.Locale;
import java.util.Comparator;
import java.util.List;

import com.electrostock.frontend.api.InventoryReportClient;
import com.electrostock.frontend.api.InventoryReportClient.LowStockItem;
import com.electrostock.frontend.api.InventoryReportClient.SalesDay;
import com.electrostock.frontend.api.InventoryReportClient.StockActivity;
import com.electrostock.frontend.api.InventoryReportClient.StockItem;

import javafx.application.Platform;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineJoin;

public class DashboardView extends VBox {

	private long salesRequestNumber;
    private static final String STOCK_GREEN = "#44B8A4";
    private static final String STOCK_RED = "#FA7068";
    private static final String STOCK_AMBER = "#EDAD48";
    private static final int PROFIT_OVERVIEW_PAGE_SIZE = 9;

    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    
    private final InventoryReportClient inventoryReports = new InventoryReportClient();
    private final List<Runnable> inventoryLoaders = new java.util.ArrayList<>();
    private final javafx.beans.property.IntegerProperty pendingReports =
            new javafx.beans.property.SimpleIntegerProperty();
    private static final DateTimeFormatter ACTIVITY_TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);

    //Constructor
    public DashboardView(Runnable onNewSale, Runnable onNewPurchase) {
        getStylesheets().add(
                DashboardView.class.getResource("dashboard.css").toExternalForm());
        getStyleClass().add("dashboard-page");

        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-heading");

        Button newSale = new Button("New sale");
        newSale.getStyleClass().add("primary-action");
        newSale.setOnAction(event -> onNewSale.run());

        Button newPurchase = new Button("New purchase");
        newPurchase.getStyleClass().add("secondary-action");
        newPurchase.setOnAction(event -> onNewPurchase.run());

        setStyle("-fx-background-color: #F3F5F9; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-background-color: #192330;");
        newSale.setGraphic(icon("New sale", "white"));
        newSale.setStyle("-fx-background-color: #098879; -fx-text-fill: white;");
        newPurchase.setGraphic(icon("New purchase", "#09193D"));
        Button refreshAll = new Button("Refresh all", icon("Refresh", "#09193D"));
        refreshAll.getStyleClass().add("secondary-action");
        refreshAll.disableProperty().bind(pendingReports.greaterThan(0));
        HBox pageHeader = new HBox(10, title, flexibleSpace(), refreshAll, newSale, newPurchase);
        pageHeader.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> salesPeriod = new ComboBox<>();
        salesPeriod.getItems().addAll("This month", "Today");
        salesPeriod.setValue("This month");
        salesPeriod.setAccessibleText("Sales period");
        salesPeriod.getStyleClass().add("period-selector");

        VBox salesContent = new VBox(12);
        salesPeriod.setOnAction(event ->
                updateSalesPanel(salesContent, salesPeriod.getValue()));

        VBox salesCard = new VBox(14,
                sectionHeader("Sales performance", salesPeriod),
                salesContent);
        salesCard.getStyleClass().add("report-card");

        VBox stockCard = createStockPanel();
        stockCard.getStyleClass().add("report-card");

        GridPane sections = new GridPane();
        sections.setHgap(12);
        sections.setVgap(14);

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
        scroll.getStyleClass().add("dashboard-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(pageHeader, scroll);
        refreshAll.setOnAction(event -> {
            updateSalesPanel(salesContent, salesPeriod.getValue());
            inventoryLoaders.forEach(Runnable::run);
        });
        Runnable fit = () -> Platform.runLater(() -> fitInitialWindow(scroll, sections));
        pendingReports.addListener((obs, oldValue, value) -> {
            if (value.intValue() == 0) fit.run();
        });
        refreshAll.fire();
        sceneProperty().addListener((obs, previous, scene) -> {
            if (scene != null) Platform.runLater(() -> {
                decorateNavigation(scene);
                if (pendingReports.get() == 0) fit.run();
            });
        });
    }

    private void fitInitialWindow(ScrollPane scroll, GridPane sections) {
        var scene = getScene();
        if (scene == null || !(scene.getWindow() instanceof javafx.stage.Stage stage)
                || !stage.isShowing() || stage.isMaximized() || stage.isFullScreen()
                || stage.getProperties().containsKey("electrostock.dashboardSized")) return;
        stage.getProperties().put("electrostock.dashboardSized", true);
        var screens = javafx.stage.Screen.getScreensForRectangle(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        var bounds = (screens.isEmpty() ? javafx.stage.Screen.getPrimary() : screens.get(0))
                .getVisualBounds();
        double width = Math.min(bounds.getWidth(), 1500);
        stage.setWidth(width);
        Platform.runLater(() -> {
            if (getScene() != scene || stage.isMaximized() || stage.isFullScreen()) return;
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            var viewport = scroll.getViewportBounds();
            double chrome = stage.getHeight() - viewport.getHeight();
            double height = Math.min(bounds.getHeight(),
                    Math.ceil(chrome + sections.prefHeight(viewport.getWidth()) + 12));
            stage.setHeight(height);
            stage.setX(Math.max(bounds.getMinX(), Math.min(stage.getX(), bounds.getMaxX() - width)));
            stage.setY(Math.max(bounds.getMinY(), Math.min(stage.getY(), bounds.getMaxY() - height)));
        });
    }

    private void colorSeries(XYChart.Series<String, Number> series, String color) {
        for (var point : series.getData()) {
            if (point.getNode() == null) point.setNode(new StackPane());
            String fill = point.getYValue().doubleValue() < 0 ? STOCK_RED : color;
            point.getNode().setStyle("-fx-bar-fill: " + fill + ";");
        }
    }

    private Label movementBadge(String type) {
        String word = type.toLowerCase(Locale.ROOT).replace('_', ' ');
        Label badge = new Label(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        String fill = switch (type) {
            case "SALE" -> "#D6E3F3";
            case "PURCHASE" -> "#C1DAFF";
            case "REVERSAL" -> "#EEE5FA";
            default -> "#D7F0E9";
        };
        badge.setStyle("-fx-background-color: " + fill + "; -fx-background-radius: 6;"
                + "-fx-text-fill: #09193D; -fx-padding: 3 10;");
        badge.setMinWidth(76);
        badge.setAlignment(Pos.CENTER);
        return badge;
    }

    private void decorateNavigation(javafx.scene.Scene scene) {
        // Navigation belongs to the application shell, outside the dashboard.
        for (var node : scene.getRoot().lookupAll(".button")) {
            if (!(node instanceof Button button)) continue;
            if (!List.of("Dashboard", "Products", "Purchases", "Sales", "Suppliers",
                    "Stock", "Reports").contains(button.getText())) continue;
            if (button.getParent() instanceof VBox sidebar
                    && sidebar.getChildren().stream().anyMatch(child ->
                            child instanceof Button b && "Dashboard".equals(b.getText()))) {
                button.setGraphic(icon(button.getText(), "white"));
                button.setGraphicTextGap(10);
                button.setAlignment(Pos.CENTER_LEFT);
                sidebar.setBackground(new javafx.scene.layout.Background(
                        new javafx.scene.layout.BackgroundFill(Color.web("#09193D"),
                                javafx.scene.layout.CornerRadii.EMPTY, Insets.EMPTY)));
            }
        }
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
            default -> "M3 20V13H6V20Z M10 20V8H13V20Z M17 20V3H20V20Z";
        };
        var shape = new javafx.scene.shape.SVGPath();
        shape.setContent(path);
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(Color.web(color));
        shape.setStrokeWidth(1.8);
        shape.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
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

    private HBox sectionHeader(String text, javafx.scene.Node control) {
        Label heading = new Label(text, icon("Reports", "#008C79"));
        heading.setGraphicTextGap(9);
        heading.getStyleClass().add("section-heading");

        HBox header = new HBox(8, heading, flexibleSpace(), control);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinHeight(34);
        return header;
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
        Label heading = new Label("Recent stock activity");
        heading.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

        HBox header = new HBox(heading);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox();
        VBox panel = new VBox(10, header, content);
        panel.getStyleClass().add("recent-stock-activity");
        panel.setPadding(new Insets(14));

        inventoryLoaders.add(() -> {
            pendingReports.set(pendingReports.get() + 1);
            content.getChildren().setAll(new Label("Loading recent activity…"));

            inventoryReports.fetchRecentActivity().whenComplete((activities, error) ->
                    Platform.runLater(() -> {
                        pendingReports.set(pendingReports.get() - 1);

                        if (error != null) {
                            content.getChildren().setAll(
                                    new Label("Couldn’t load activity. Check the backend and refresh."));
                        } else if (activities.isEmpty()) {
                            content.getChildren().setAll(
                                    new Label("No stock movements recorded yet."));
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

        // The product name gets the space left by the four shorter columns.
        ColumnConstraints productColumn = new ColumnConstraints(75, 175, Double.MAX_VALUE);
        productColumn.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().addAll(
                new ColumnConstraints(98, 106, 106),
                new ColumnConstraints(73, 96, 96),
                productColumn,
                new ColumnConstraints(42, 50, 50),
                new ColumnConstraints(94, 106, 106));

        addActivityRow(table, 0, activities.size(),
                new Label("Movement"), new Label("SKU"), new Label("Product name"),
                new Label("Qty"), new Label("Time"));

        int row = 1;
        for (StockActivity activity : activities) {
            Label quantity = new Label("%+d".formatted(activity.quantityChange()));
            quantity.getStyleClass().add("stock-activity-quantity");

            String name = activity.productName();
            Label product = new Label(name == null || name.isBlank() ? "—" : name);
            product.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            if (name != null && !name.isBlank()) {
                product.setTooltip(new javafx.scene.control.Tooltip(name));
            }
            Label sku = new Label(activity.sku());
            sku.setTooltip(new javafx.scene.control.Tooltip(activity.sku()));

            addActivityRow(table, row++, activities.size(), movementBadge(activity.movementType()),
                    sku, product, quantity,
                    new Label(ACTIVITY_TIME.format(activity.movementAt())));
        }
        return table;
    }

    private void addActivityRow(GridPane table, int row, int lastRow, Label... labels) {
        for (int column = 0; column < labels.length; column++) {
            Label label = labels[column];
            if (row == 0 || column != 0) label.setMinWidth(0);
            StackPane cell = new StackPane(label);
            cell.setAlignment(column == 3 ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            cell.getStyleClass().add("stock-activity-cell");
            if (row == 0) cell.getStyleClass().add("stock-activity-heading");
            if (row != 0 && row % 2 == 0) cell.getStyleClass().add("stock-activity-even");
            if (row == lastRow) cell.getStyleClass().add("stock-activity-last-row");
            if (column == labels.length - 1) cell.getStyleClass().add("stock-activity-last-column");
            table.add(cell, column, row);
        }
    }
    private VBox createLowStockPanel() {
        Label heading = new Label("Stock availability");
        heading.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

        HBox header = new HBox(heading);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox();
        VBox panel = new VBox(10, header, content);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #D7DFE7; -fx-border-radius: 8;");

        inventoryLoaders.add(() -> {
            pendingReports.set(pendingReports.get() + 1);
            content.getChildren().setAll(new Label("Loading stock…"));

            inventoryReports.fetchStockAvailability().whenComplete((items, error) ->
                    Platform.runLater(() -> {
                        pendingReports.set(pendingReports.get() - 1);

                        if (error != null) {
                            content.getChildren().setAll(
                                    new Label("Couldn’t load stock. Check the backend and refresh."));
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
        cards.setHgap(8);
        cards.setVgap(8);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            cards.getColumnConstraints().add(column);
        }
        cards.add(stockCount("Total products", total, -1, "#243B62"), 0, 0);
        cards.add(stockCount("In stock", healthy, total, STOCK_GREEN), 1, 0);
        cards.add(stockCount("Low stock", low, total, STOCK_AMBER), 0, 1);
        cards.add(stockCount("Out of stock", out, total, STOCK_RED), 1, 1);
        HBox.setHgrow(cards, Priority.ALWAYS);
        HBox body = new HBox(14, donut, cards);
        body.setAlignment(Pos.CENTER_LEFT);
        return new VBox(body);
    }

    private Canvas availabilityDonut(int healthy, int low, int out) {
        Canvas ring = new Canvas(184, 184);
        GraphicsContext pen = ring.getGraphicsContext2D();
        double center = ring.getWidth() / 2;
        double outer = center - 3;
        double inner = 53;
        double mid = (inner + outer) / 2;
        int[] counts = {healthy, low, out};
        String[] colors = {STOCK_GREEN, STOCK_AMBER, STOCK_RED};
        int total = healthy + low + out;
        pen.setLineJoin(StrokeLineJoin.ROUND);
        pen.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));
        pen.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        pen.setTextBaseline(javafx.geometry.VPos.CENTER);

        if (total == 0) {
            pen.setStroke(Color.web("#E4E9EF"));
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
                pen.setFill(Color.web("#111C28"));
                pen.fillText(percentLabel(n, total), center + mid * Math.cos(angle),
                        center - mid * Math.sin(angle), outer - inner - 2);
            }
            start -= sweep;
        }
        ring.setAccessibleRole(javafx.scene.AccessibleRole.IMAGE_VIEW);
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

    private VBox stockCount(String title, int count, int total, String color) {
        Label caption = new Label(title);
        caption.setStyle("-fx-font-size: 14px; -fx-text-fill: #182331;");
        Label number = new Label(String.valueOf(count));
        number.setStyle("-fx-font-size: 21px; -fx-font-weight: bold; -fx-text-fill: #101923;");
        HBox values = new HBox(8, number);
        values.setAlignment(Pos.BASELINE_LEFT);
        if (total >= 0) {
            Label percent = new Label(percentLabel(count, total));
            percent.setStyle("-fx-text-fill: #394757; -fx-font-size: 13px; -fx-font-weight: bold;");
            values.getChildren().add(percent);
        }
        VBox card = new VBox(2, caption, values);
        card.setPadding(new Insets(6, 11, 6, 11));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 5;"
                + "-fx-border-color: transparent transparent transparent " + color + ";"
                + "-fx-border-width: 0 0 0 4;");
        return card;
    }

    private VBox createMetricCard(String title, String value, boolean negative) {
        Label caption = new Label(title);
        caption.setStyle("-fx-font-size: 14px; -fx-text-fill: #202C39;");

        Label amount = new Label(value);
        String valueColor = negative ? "#A52F30" : "#172B40";
        amount.setStyle(
                "-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: "
                + valueColor + ";");

        VBox text = new VBox(4, caption, amount);
        HBox contents = new HBox(8, icon(title, "#008C79"), text);
        contents.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(contents);
        card.setPadding(new Insets(12));
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #D7DFE7; -fx-border-radius: 8;");
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

        panel.getChildren().setAll(new Label("Loading sales…"));

        inventoryReports.fetchDailySales(from, today).whenComplete((days, error) ->
                Platform.runLater(() -> {
                    pendingReports.set(pendingReports.get() - 1);
                    // A newer period selection may have finished loading first.
                    if (requestNumber != salesRequestNumber) {
                        return;
                    }

                    if (error != null) {
                        Button retry = new Button("Retry");
                        retry.setOnAction(event -> updateSalesPanel(panel, period));
                        panel.getChildren().setAll(new Label("Couldn’t load sales."), retry);
                        return;
                    }

                    panel.getChildren().setAll(createSalesSummary(days));
                    if (days.isEmpty()) {
                        panel.getChildren().add(new Label("No confirmed sales in this period."));
                    } else {
                        panel.getChildren().add(createSalesChart(days));
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

        return new HBox(8,
                createMetricCard("Revenue", "%,.2f MAD".formatted(revenue), false),
                createMetricCard("Gross profit", "%,.2f MAD".formatted(profit),
                        profit.signum() < 0),
                createMetricCard("Units sold", String.valueOf(units), false));
    }

    private BarChart<String, Number> createSalesChart(List<SalesDay> selectedDays) {
        CategoryAxis days = new CategoryAxis();
        days.setTickLabelFill(Color.web("#192330"));
        days.setTickLabelFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));
        NumberAxis amounts = new NumberAxis();
        amounts.setTickLabelFill(Color.web("#192330"));
        amounts.setTickLabelFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));
        amounts.setLabel("Amount (MAD)");

        BarChart<String, Number> chart = new BarChart<>(days, amounts);
        chart.setPrefHeight(275);
        chart.getStyleClass().add("sales-chart");
        chart.setTitle("Daily sales and gross profit");
        chart.setAnimated(false);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);

        XYChart.Series<String, Number> revenue = new XYChart.Series<>();
        revenue.setName("Revenue");

        XYChart.Series<String, Number> profit = new XYChart.Series<>();
        profit.setName("Gross profit");

        for (SalesDay day : selectedDays) {
            String label = day.date().format(DAY_LABEL);
            revenue.getData().add(new XYChart.Data<>(label, day.revenueMad()));
            XYChart.Data<String, Number> profitPoint =
                    new XYChart.Data<>(label, day.profitMad());

            if (day.profitMad().signum() < 0) {
                StackPane lossBar = new StackPane();
                lossBar.setStyle("-fx-bar-fill: #F74436;");
                profitPoint.setNode(lossBar);
            }

            profit.getData().add(profitPoint);
        }

        colorSeries(revenue, "#086EF9");
        colorSeries(profit, STOCK_GREEN);
        chart.getData().add(revenue);
        chart.getData().add(profit);
        return chart;
    }

    private VBox createStockPanel() {
        VBox content = new VBox(12);
        Label stockTitle = new Label("Stock on hand now", icon("Products", "#008C79"));
        stockTitle.setGraphicTextGap(9);
        stockTitle.getStyleClass().add("section-heading");
        stockTitle.setMinHeight(34);

        VBox panel = new VBox(14,
                stockTitle,
                content);

        ToggleButton overview = new ToggleButton("Overview");
        ToggleButton gains = new ToggleButton("Largest gains");
        ToggleButton losses = new ToggleButton("Largest losses");

        ToggleGroup stockFilter = new ToggleGroup();
        List<ToggleButton> buttons = List.of(overview, gains, losses);
        String segmentStyle = "-fx-padding: 6 10; -fx-background-radius: 4;";

        for (ToggleButton button : buttons) {
            button.setToggleGroup(stockFilter);
            button.styleProperty().bind(
                    javafx.beans.binding.Bindings.when(button.selectedProperty())
                            .then(segmentStyle
                                    + "-fx-background-color: #008C79; -fx-text-fill: white;")
                            .otherwise(segmentStyle
                                    + "-fx-background-color: #EDF2F5; -fx-text-fill: #172B40;"));
        }
        overview.setSelected(true);

        Label title = new Label("Potential profit by product");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

        HBox filterBar = new HBox(overview, gains, losses);
        VBox stockRows = new VBox(6);

        inventoryLoaders.add(() -> {
            pendingReports.set(pendingReports.get() + 1);
            content.getChildren().setAll(new Label("Loading inventory…"));

            inventoryReports.fetchInventoryValuation().whenComplete((items, error) ->
                    Platform.runLater(() -> {
                        pendingReports.set(pendingReports.get() - 1);

                        if (error != null) {
                            content.getChildren().setAll(new Label(
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
                        content.getChildren().setAll(
                                createStockSummary(items), title, filterBar, stockRows);
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

        return new HBox(8,
                createMetricCard("Stock at cost", "%,.2f MAD".formatted(totalCost), false),
                createMetricCard("Potential sales", "%,.2f MAD".formatted(potentialSales), false),
                createMetricCard("Potential profit", "%,.2f MAD".formatted(potentialProfit),
                        potentialProfit.signum() < 0));
    }
    private void showProfitOverview(VBox rows, List<StockItem> items) {
        BigDecimal net = items.stream()
                .map(StockItem::potentialProfitMad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Label total = new Label(String.format(
                Locale.ENGLISH, "Net potential profit   %,.2f MAD", net));
        total.getStyleClass().add("profit-total");

        List<StockItem> products = items.stream()
                .filter(item -> item.potentialProfitMad().signum() != 0)
                .sorted(Comparator.comparing(StockItem::potentialProfitMad)
                        .thenComparing(StockItem::sku))
                .toList();

        rows.getChildren().setAll(total);

        if (products.isEmpty()) {
            rows.getChildren().add(new Label("No products with a potential gain or loss."));
        } else if (products.size() <= PROFIT_OVERVIEW_PAGE_SIZE) {
            rows.getChildren().add(createProfitChart(products));
        } else {
            addProfitOverviewPages(rows, total, products);
        }
    }

    private void addProfitOverviewPages(VBox rows, Label total, List<StockItem> products) {
        int pageCount = (products.size() - 1) / PROFIT_OVERVIEW_PAGE_SIZE + 1;
        var page = new javafx.beans.property.SimpleIntegerProperty(0);
        Button previous = new Button("Previous");
        Button next = new Button("Next");
        Label pageLabel = new Label();
        StackPane chartSlot = new StackPane();
        HBox header = new HBox(8, total, flexibleSpace(), previous, pageLabel, next);
        header.setAlignment(Pos.CENTER_LEFT);

        previous.disableProperty().bind(page.isEqualTo(0));
        next.disableProperty().bind(page.isEqualTo(pageCount - 1));
        previous.setOnAction(event -> page.set(page.get() - 1));
        next.setOnAction(event -> page.set(page.get() + 1));

        // Use one MAD scale across pages so bar lengths stay comparable.
        Runnable renderPage = () -> {
            int index = page.get();
            int from = index * PROFIT_OVERVIEW_PAGE_SIZE;
            int to = Math.min(from + PROFIT_OVERVIEW_PAGE_SIZE, products.size());
            pageLabel.setText((index + 1) + " / " + pageCount);
            chartSlot.getChildren().setAll(
                    createProfitChart(products.subList(from, to), products));
        };
        page.addListener((obs, oldPage, newPage) -> renderPage.run());
        renderPage.run();
        rows.getChildren().setAll(header, chartSlot);
    }

    private void updateStockProfitRows(
            VBox rows, List<StockItem> items, boolean losses) {

        Comparator<StockItem> order =
                Comparator.comparing(StockItem::potentialProfitMad);

        List<StockItem> products = items.stream()
                .filter(item -> item.potentialProfitMad().signum() == (losses ? -1 : 1))
                .sorted((losses ? order : order.reversed()).thenComparing(StockItem::sku))
                .limit(5)
                .toList();

        if (products.isEmpty()) {
            rows.getChildren().setAll(new Label(losses
                    ? "No products with a potential loss."
                    : "No products with a potential gain."));
            return;
        }

        rows.getChildren().setAll(createProfitChart(products));
    }

    private Region createProfitChart(List<StockItem> products) {
        return createProfitChart(products, products);
    }

    private Region createProfitChart(List<StockItem> products, List<StockItem> scaleProducts) {
        boolean compact = products.size() > 5;
        NumberAxis amounts = new NumberAxis();
        amounts.setTickLabelFill(Color.web("#192330"));
        amounts.setTickLabelFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));
        amounts.setLabel("Potential profit (MAD)");
        amounts.setForceZeroInRange(true);
        amounts.setMinorTickVisible(false);

        double min = Math.min(0, scaleProducts.stream().map(StockItem::potentialProfitMad)
                .min(BigDecimal::compareTo).orElseThrow().doubleValue());
        double max = Math.max(0, scaleProducts.stream().map(StockItem::potentialProfitMad)
                .max(BigDecimal::compareTo).orElseThrow().doubleValue());
        amounts.setAutoRanging(false);
        amounts.setTickLabelRotation(0);
        amounts.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            private final java.text.DecimalFormat format = new java.text.DecimalFormat("0.###",
                    java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH));
            public String toString(Number number) {
                double value = number.doubleValue();
                double size = Math.abs(value);
                if (size >= 1e12) return format.format(value / 1e12) + "T";
                if (size >= 1e9) return format.format(value / 1e9) + "B";
                if (size >= 1e6) return format.format(value / 1e6) + "M";
                if (size >= 1000) return format.format(value / 1000) + "k";
                return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }
            public Number fromString(String text) { throw new UnsupportedOperationException(); }
        });
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
        skus.setTickLabelFill(Color.web("#192330"));
        skus.setTickLabelFont(javafx.scene.text.Font.font(
                "System", javafx.scene.text.FontWeight.BOLD, compact ? 12 : 13));

        // JavaFX draws the first vertical category at the bottom.
        // Reverse the axis categories to show the first ranked product at the top.
        var categories = javafx.collections.FXCollections.<String>observableArrayList();
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
        chart.setCategoryGap(compact ? 4 : 9);
        chart.setBarGap(0);

        XYChart.Series<Number, String> series = new XYChart.Series<>();

        for (StockItem product : products) {
            BigDecimal profit = product.potentialProfitMad();
            boolean loss = profit.signum() < 0;
            String formatted = String.format(Locale.ENGLISH, "%,.2f", profit);

            Label value = new Label(formatted);
            value.getStyleClass().add("profit-bar-value");
            value.setMouseTransparent(true);
            value.setStyle("-fx-text-fill: #101923; -fx-font-size: "
                    + (compact ? 12 : 13) + "px; -fx-font-weight: bold;");

            StackPane bar = new StackPane(value);
            bar.setAlignment(loss ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            bar.setPadding(new Insets(0, 5, 0, 5));
            bar.setStyle("-fx-bar-fill: " + (loss ? STOCK_RED : STOCK_GREEN) + ";");

            // Keep the bar proportional even when the amount is too small for text.
            value.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> bar.getWidth() >= value.prefWidth(-1) + 10,
                    bar.widthProperty(), value.fontProperty()));

            String detail = product.sku() + " · " + formatted + " MAD";
            javafx.scene.control.Tooltip.install(bar, new javafx.scene.control.Tooltip(detail));
            bar.setAccessibleText(detail);

            XYChart.Data<Number, String> point =
                    new XYChart.Data<>(profit, product.sku());
            point.setNode(bar);
            series.getData().add(point);
        }

        chart.getData().add(series);

        // Keep the full chart, including its MAD axis, inside the card.
        // A compact overview fits nine rows without an inner ScrollPane.
        double height = 75 + Math.min(36.0 * products.size(), 185);
        chart.setMinHeight(height);
        chart.setPrefHeight(height);
        return chart;
    }
}
