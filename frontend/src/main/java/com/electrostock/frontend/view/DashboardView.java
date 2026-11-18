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
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

public class DashboardView extends VBox {

	private long salesRequestNumber;

    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    
    private final InventoryReportClient inventoryReports = new InventoryReportClient();
    
    private static final DateTimeFormatter ACTIVITY_TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);

    //Constructor
    public DashboardView(Runnable onNewSale, Runnable onNewPurchase) {
        getStylesheets().add(
                DashboardView.class.getResource("dashboard.css").toExternalForm());
        setSpacing(20);
        setPadding(new Insets(20));

        Label title = new Label("Dashboard");
        title.getStyleClass().add("dashboard-title");
        Button newSale = new Button("New sale");
        Button newPurchase = new Button("New purchase");
        newSale.setOnAction(event -> onNewSale.run());
        newPurchase.setOnAction(event -> onNewPurchase.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, title, spacer, newSale, newPurchase);
        header.setAlignment(Pos.CENTER_LEFT);
        GridPane sections = new GridPane();
        sections.setHgap(20);

        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            sections.getColumnConstraints().add(column);
        }

     // Left column
        ComboBox<String> salesPeriod = new ComboBox<>();
        salesPeriod.getItems().addAll("This month", "Today");
        salesPeriod.setValue("This month");
        salesPeriod.setAccessibleText("Sales period");

        HBox salesHeader = new HBox(12, new Label("Sales performance"), salesPeriod);
        salesHeader.setAlignment(Pos.CENTER_LEFT);

        VBox salesPanel = new VBox(12);
        updateSalesPanel(salesPanel, salesPeriod.getValue());

        salesPeriod.setOnAction(event ->
                updateSalesPanel(salesPanel, salesPeriod.getValue()));

        sections.add(salesHeader, 0, 0);
        sections.add(salesPanel, 0, 1);

        // Right column
        sections.add(new Label("Stock on hand now"), 1, 0);
        sections.add(createStockPanel(), 1, 1);

        sections.setVgap(10);
        sections.add(createLowStockPanel(), 0, 2);
        sections.add(createRecentActivityPanel(), 1, 2);

        ScrollPane reportScroll = new ScrollPane(sections);
        reportScroll.setFitToWidth(true);
        reportScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reportScroll.setMinHeight(0);
        reportScroll.getStyleClass().add("dashboard-scroll");

        VBox.setVgrow(reportScroll, Priority.ALWAYS);
        getChildren().addAll(header, reportScroll);
        
    }
    private VBox createRecentActivityPanel() {
        Label heading = new Label("Recent stock activity");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button refresh = new Button("Refresh");
        HBox header = new HBox(12, heading, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox();
        VBox panel = new VBox(10, header, content);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #D7DFE7; -fx-border-radius: 8;");

        refresh.setOnAction(event -> {
            refresh.setDisable(true);
            content.getChildren().setAll(new Label("Loading recent activity…"));

            inventoryReports.fetchRecentActivity().whenComplete((activities, error) ->
                    Platform.runLater(() -> {
                        refresh.setDisable(false);

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

        refresh.fire();
        return panel;
    }

    private GridPane createActivityTable(List<StockActivity> activities) {
        GridPane table = new GridPane();
        table.setHgap(12);
        table.setVgap(8);

        ColumnConstraints typeColumn = new ColumnConstraints();
        ColumnConstraints skuColumn = new ColumnConstraints();
        skuColumn.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().addAll(typeColumn, skuColumn);

        table.addRow(0, new Label("Movement"), new Label("SKU"),
                new Label("Units"), new Label("When"));

        int row = 1;
        for (StockActivity activity : activities) {
            Label units = new Label("%+d".formatted(activity.quantityChange()));
            units.setStyle("-fx-font-weight: bold;");

            table.addRow(row++, new Label(activity.movementType()),
                    new Label(activity.sku()), units,
                    new Label(ACTIVITY_TIME.format(activity.movementAt())));
        }

        return table;
    }
    private VBox createLowStockPanel() {
        Label heading = new Label("Stock availability");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button refresh = new Button("Refresh");
        HBox header = new HBox(12, heading, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox();
        VBox panel = new VBox(10, header, content);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #D7DFE7; -fx-border-radius: 8;");

        refresh.setOnAction(event -> {
            refresh.setDisable(true);
            content.getChildren().setAll(new Label("Loading stock…"));

            inventoryReports.fetchStockAvailability().whenComplete((items, error) ->
                    Platform.runLater(() -> {
                        refresh.setDisable(false);

                        if (error != null) {
                            content.getChildren().setAll(
                                    new Label("Couldn’t load stock. Check the backend and refresh."));
                            return;
                        }

                        content.getChildren().setAll(createAvailabilityContent(items));
                        if (items.isEmpty()) {
                            content.getChildren().setAll(
                                    new Label("No products are at or below their threshold."));
                        } else {
                        	content.getChildren().setAll(createAvailabilityContent(items));
                        }
                    }));
        });

        refresh.fire();
        return panel;
    }

    private VBox createAvailabilityContent(List<LowStockItem> items) {
        if (items.isEmpty()) {
            return new VBox(new Label("No active products."));
        }

        List<LowStockItem> alerts = items.stream()
                .filter(item -> item.stockQuantity() <= item.lowStockThreshold())
                .toList();

        int out = (int) alerts.stream()
                .filter(item -> item.stockQuantity() <= 0)
                .count();
        int low = alerts.size() - out;
        int above = items.size() - alerts.size();

        Color teal = Color.web("#087869");
        Color amber = Color.web("#AB650F");
        Color red = Color.web("#B83B42");

        Canvas ring = new Canvas(124, 124);
        GraphicsContext pen = ring.getGraphicsContext2D();

        int[] counts = {above, low, out};
        Color[] colors = {teal, amber, red};
        double start = 90;

        for (int i = 0; i < counts.length; i++) {
            double sweep = 360.0 * counts[i] / items.size();
            if (counts[i] > 0) {
                pen.setFill(colors[i]);
                pen.fillArc(2, 2, 120, 120, start, -sweep, ArcType.ROUND);
            }
            start -= sweep;
        }

        pen.setFill(Color.WHITE);
        pen.fillOval(17, 17, 90, 90);

        Label count = new Label(alerts.size() + " of " + items.size());
        count.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #172B40;");
        Label caption = new Label("need restocking");
        caption.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");

        VBox center = new VBox(2, count, caption);
        center.setAlignment(Pos.CENTER);
        StackPane donut = new StackPane(ring, center);

        VBox details = new VBox(8);
        if (alerts.isEmpty()) {
            details.getChildren().add(new Label("All active products are above threshold."));
        } else {
            details.getChildren().add(createLowStockTable(alerts));
        }

        HBox body = new HBox(18, donut, details);
        body.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(details, Priority.ALWAYS);

        javafx.scene.layout.FlowPane legend = new javafx.scene.layout.FlowPane(14, 4);
        legend.getChildren().addAll(
                availabilityKey(teal, "Above threshold", above),
                availabilityKey(amber, "Low stock", low),
                availabilityKey(red, "Out of stock", out));

        return new VBox(8, body, legend);
    }

    private HBox availabilityKey(Color color, String label, int count) {
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4, color);
        HBox key = new HBox(5, dot, new Label(label + " · " + count));
        key.setAlignment(Pos.CENTER_LEFT);
        return key;
    }
    private GridPane createLowStockTable(List<LowStockItem> items) {
        GridPane table = new GridPane();
        table.setHgap(16);
        table.setVgap(8);

        ColumnConstraints skuColumn = new ColumnConstraints();
        skuColumn.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().add(skuColumn);

        table.addRow(0, new Label("SKU"), new Label("On hand"), new Label("Threshold"));

        List<LowStockItem> sorted = items.stream()
                .sorted(Comparator.comparingLong(LowStockItem::stockQuantity)
                        .thenComparing(LowStockItem::sku))
                .toList();

        int row = 1;
        for (LowStockItem item : sorted) {
            Label quantity = new Label(String.valueOf(item.stockQuantity()));
            if (item.stockQuantity() == 0) {
                quantity.setStyle("-fx-text-fill: #B83B42; -fx-font-weight: bold;");
            }

            table.addRow(row++, new Label(item.sku()), quantity,
                    new Label(String.valueOf(item.lowStockThreshold())));
        }
        return table;
    }
    private VBox createMetricCard(String title, String value, boolean negative) {
        Label caption = new Label(title);
        caption.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");

        Label amount = new Label(value);
        String valueColor = negative ? "#B83B42" : "#172B40";
        amount.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: "
                + valueColor + ";");

        VBox card = new VBox(6, caption, amount);
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

        panel.getChildren().setAll(new Label("Loading sales…"));

        inventoryReports.fetchDailySales(from, today).whenComplete((days, error) ->
                Platform.runLater(() -> {
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
        NumberAxis amounts = new NumberAxis();
        amounts.setLabel("Amount (MAD)");

        BarChart<String, Number> chart = new BarChart<>(days, amounts);
        chart.setPrefHeight(275);
        chart.getStyleClass().add("sales-chart");
        chart.setTitle("Daily sales and gross profit");
        chart.setAnimated(false);

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
                lossBar.setStyle("-fx-bar-fill: #B83B42;");
                profitPoint.setNode(lossBar);
            }

            profit.getData().add(profitPoint);
        }

        chart.getData().add(revenue);
        chart.getData().add(profit);
        return chart;
    }

    private VBox createStockPanel() {
        Button refresh = new Button("Refresh");
        VBox content = new VBox(12);
        VBox panel = new VBox(12, refresh, content);
        RadioButton gains = new RadioButton("Largest gains");
        RadioButton losses = new RadioButton("Largest losses");

        ToggleGroup stockFilter = new ToggleGroup();
        gains.setToggleGroup(stockFilter);
        losses.setToggleGroup(stockFilter);
        losses.setSelected(true);

        HBox filterBar = new HBox(12, gains, losses);

        VBox stockRows = new VBox(8);


        Label stockChartTitle = new Label("Potential profit by product");
        stockChartTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        refresh.setOnAction(event -> {
            refresh.setDisable(true);
            content.getChildren().setAll(new Label("Loading inventory…"));

            inventoryReports.fetchInventoryValuation().whenComplete((items, error) ->
                    Platform.runLater(() -> {
                        refresh.setDisable(false);
                        if (error != null) {
                            content.getChildren().setAll(new Label(
                                    "Couldn’t load inventory. Check the backend and refresh."));
                            return;
                        }

                        gains.setOnAction(e -> updateStockProfitRows(stockRows, items, false));
                        losses.setOnAction(e -> updateStockProfitRows(stockRows, items, true));
                        updateStockProfitRows(stockRows, items, losses.isSelected());
                        content.getChildren().setAll(
                                createStockSummary(items), stockChartTitle, filterBar, stockRows);
                    }));
        });

        refresh.fire();
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

    private void updateStockProfitRows(VBox rows, List<StockItem> items, boolean showLosses) {
        rows.getChildren().clear();

        // Use the same scale for gains and losses.
        BigDecimal largestMagnitude = items.stream()
                .map(item -> item.potentialProfitMad().abs())
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        Comparator<StockItem> byProfit =
                Comparator.comparing(StockItem::potentialProfitMad);

        List<StockItem> rankedItems = items.stream()
                .filter(item -> showLosses
                        ? item.potentialProfitMad().signum() < 0
                        : item.potentialProfitMad().signum() > 0)
                .sorted(showLosses ? byProfit : byProfit.reversed())
                .limit(5)
                .toList();

        if (rankedItems.isEmpty()) {
            rows.getChildren().add(new Label(showLosses
                    ? "No products with a potential loss."
                    : "No products with a potential gain."));
            return;
        }

        for (StockItem item : rankedItems) {
            rows.getChildren().add(createStockProfitRow(item, largestMagnitude));
        }
    }

    private HBox createStockProfitRow(StockItem item, BigDecimal largestMagnitude) {
        BigDecimal profit = item.potentialProfitMad();
        boolean loss = profit.signum() < 0;
        String color = loss ? "#B83B42" : "#087869";

        Label sku = new Label(item.sku());
        sku.setMinWidth(84);
        sku.setPrefWidth(84);

        // The two equal halves meet at zero.
        StackPane left = new StackPane();
        StackPane right = new StackPane();
        left.setAlignment(Pos.CENTER_RIGHT);
        right.setAlignment(Pos.CENTER_LEFT);
        left.setStyle("-fx-border-color: transparent #94A3B8 transparent transparent;");

        GridPane track = new GridPane();
        for (int i = 0; i < 2; i++) {
            ColumnConstraints half = new ColumnConstraints();
            half.setPercentWidth(50);
            track.getColumnConstraints().add(half);
        }
        track.add(left, 0, 0);
        track.add(right, 1, 0);
        track.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(track, Priority.ALWAYS);

        StackPane side = loss ? left : right;
        double fraction = profit.abs().doubleValue() / largestMagnitude.doubleValue();

        Region bar = new Region();
        bar.setMinSize(0, 12);
        bar.setPrefHeight(12);
        bar.setMaxHeight(12);
        bar.maxWidthProperty().bind(side.widthProperty().multiply(fraction));
        bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        side.getChildren().add(bar);

        Label amount = new Label("%,.2f MAD".formatted(profit));
        amount.setMinWidth(120);
        amount.setAlignment(Pos.CENTER_RIGHT);
        amount.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");

        HBox row = new HBox(10, sku, track, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }
}