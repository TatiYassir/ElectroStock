package com.electrostock.frontend.view;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Locale;
import java.util.Comparator;
import java.util.List;

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
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class DashboardView extends VBox {

    private record SalesDay(LocalDate date, int revenueMad, int profitMad, int unitsSold) {}

    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    // The sample dashboard is shown as of November 17.
    private static final LocalDate SAMPLE_TODAY = LocalDate.of(2026, 11, 17);

    private final List<SalesDay> sampleDays = List.of(
            new SalesDay(LocalDate.of(2026, 11, 15), 1500, 420, 1),
            new SalesDay(LocalDate.of(2026, 11, 16), 2000, 1000, 1),
            new SalesDay(LocalDate.of(2026, 11, 17), 1450, -550, 1)
    );

    // The selling price is per unit. The stock cost covers all units of this SKU.
    private record StockItem(String sku, int quantity,
                             BigDecimal sellingPriceMad, BigDecimal stockCostMad) {

        BigDecimal potentialSalesMad() {
            return sellingPriceMad.multiply(BigDecimal.valueOf(quantity));
        }

        BigDecimal potentialProfitMad() {
            return potentialSalesMad().subtract(stockCostMad);
        }
    }

    private final List<StockItem> sampleStock = List.of(
            new StockItem("MON-001", 2,
                    new BigDecimal("1450.00"), new BigDecimal("14000.00")),
            new StockItem("MON-002", 9,
                    new BigDecimal("1500.00"), new BigDecimal("9846.4582"))
    );
    private record LowStockItem(String sku, int quantity, int threshold) {}

    private final List<LowStockItem> sampleLowStock = List.of(
            new LowStockItem("TEST-ZERO-STOCK-001", 0, 0),
            new LowStockItem("WARRANTY-TEST-001", 0, 0),
            new LowStockItem("WARRANTY-TEST-002", 0, 0)
    );
    private record StockActivity(
            String movementType, String sku, int quantityChange, LocalDateTime movementAt) {}

    private static final DateTimeFormatter ACTIVITY_TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);

    private final List<StockActivity> sampleActivity = List.of(
            new StockActivity("SALE", "WARRANTY-TEST-002", -1,
                    LocalDateTime.parse("2026-11-17T15:05:13")),
            new StockActivity("PURCHASE", "WARRANTY-TEST-002", 1,
                    LocalDateTime.parse("2026-11-17T15:04:02")),
            new StockActivity("SALE", "MON-001", -1,
                    LocalDateTime.parse("2026-11-17T14:52:44"))
    );
    //Constructor
    public DashboardView() {
        getStylesheets().add(
                DashboardView.class.getResource("dashboard.css").toExternalForm());
        setSpacing(20);
        setPadding(new Insets(20));

        Label title = new Label("Dashboard");
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
        RadioButton gains = new RadioButton("Largest gains");
        RadioButton losses = new RadioButton("Largest losses");

        ToggleGroup stockFilter = new ToggleGroup();
        gains.setToggleGroup(stockFilter);
        losses.setToggleGroup(stockFilter);
        losses.setSelected(true);

        HBox filterBar = new HBox(12, gains, losses);
        sections.add(new Label("Stock on hand now"), 1, 0);

        VBox stockRows = new VBox(8);
        updateStockProfitRows(stockRows, true);

        gains.setOnAction(event -> updateStockProfitRows(stockRows, false));
        losses.setOnAction(event -> updateStockProfitRows(stockRows, true));

        Label stockChartTitle = new Label("Potential profit by product");
        stockChartTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox stockPanel = new VBox(
                12, createStockSummary(), stockChartTitle, filterBar, stockRows);
        sections.add(stockPanel, 1, 1);

        sections.setVgap(10);
        sections.add(createLowStockPanel(), 0, 2);
        sections.add(createRecentActivityPanel(), 1, 2);

        getChildren().addAll(title, sections);
    }
    private VBox createRecentActivityPanel() {
        Label heading = new Label("Recent stock activity");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox panel = new VBox(10, heading);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #D7DFE7; -fx-border-radius: 8;");

        if (sampleActivity.isEmpty()) {
            panel.getChildren().add(new Label("No stock movements recorded yet."));
            return panel;
        }

        GridPane table = new GridPane();
        table.setHgap(12);
        table.setVgap(8);

        ColumnConstraints typeColumn = new ColumnConstraints();
        ColumnConstraints skuColumn = new ColumnConstraints();
        skuColumn.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().addAll(typeColumn, skuColumn);

        table.addRow(0, new Label("Movement"), new Label("SKU"),
                new Label("Units"), new Label("When"));

        List<StockActivity> latest = sampleActivity.stream()
                .sorted(Comparator.comparing(StockActivity::movementAt).reversed())
                .limit(5)
                .toList();

        int row = 1;
        for (StockActivity activity : latest) {
            Label units = new Label("%+d".formatted(activity.quantityChange()));
            units.setStyle("-fx-font-weight: bold;");

            table.addRow(row++,
                    new Label(activity.movementType()),
                    new Label(activity.sku()),
                    units,
                    new Label(ACTIVITY_TIME.format(activity.movementAt())));
        }

        panel.getChildren().add(table);
        return panel;
    }
    private VBox createLowStockPanel() {
        Label heading = new Label("Low stock (" + sampleLowStock.size() + ")");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox panel = new VBox(10, heading);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #D7DFE7; -fx-border-radius: 8;");

        if (sampleLowStock.isEmpty()) {
            panel.getChildren().add(new Label("All products are above their stock threshold."));
            return panel;
        }

        GridPane table = new GridPane();
        table.setHgap(16);
        table.setVgap(8);

        ColumnConstraints skuColumn = new ColumnConstraints();
        skuColumn.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().add(skuColumn);

        table.addRow(0, new Label("SKU"), new Label("On hand"), new Label("Threshold"));

        List<LowStockItem> sortedItems = sampleLowStock.stream()
                .sorted(Comparator.comparingInt(LowStockItem::quantity)
                        .thenComparing(LowStockItem::sku))
                .toList();

        int row = 1;
        for (LowStockItem item : sortedItems) {
            Label quantity = new Label(String.valueOf(item.quantity()));
            if (item.quantity() == 0) {
                quantity.setStyle("-fx-text-fill: #B83B42; -fx-font-weight: bold;");
            }

            table.addRow(row++, new Label(item.sku()), quantity,
                    new Label(String.valueOf(item.threshold())));
        }

        panel.getChildren().add(table);
        return panel;
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
        LocalDate start = "Today".equals(period)
                ? SAMPLE_TODAY
                : SAMPLE_TODAY.withDayOfMonth(1);

        List<SalesDay> selectedDays = sampleDays.stream()
                .filter(day -> !day.date().isBefore(start)
                        && !day.date().isAfter(SAMPLE_TODAY))
                .toList();

        panel.getChildren().setAll(
                createSalesSummary(selectedDays),
                createSalesChart(selectedDays));
    }

    private HBox createSalesSummary(List<SalesDay> selectedDays) {
        int revenue = 0;
        int profit = 0;
        int units = 0;

        for (SalesDay day : selectedDays) {
            revenue += day.revenueMad();
            profit += day.profitMad();
            units += day.unitsSold();
        }

        return new HBox(8,
                createMetricCard("Revenue", "%,d MAD".formatted(revenue), false),
                createMetricCard("Gross profit", "%,d MAD".formatted(profit), profit < 0),
                createMetricCard("Units sold", String.valueOf(units), false));
    }

    private BarChart<String, Number> createSalesChart(List<SalesDay> selectedDays) {
        CategoryAxis days = new CategoryAxis();
        NumberAxis amounts = new NumberAxis();
        amounts.setLabel("Amount (MAD)");

        BarChart<String, Number> chart = new BarChart<>(days, amounts);
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

            if (day.profitMad() < 0) {
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

    private HBox createStockSummary() {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal potentialSales = BigDecimal.ZERO;

        for (StockItem item : sampleStock) {
            totalCost = totalCost.add(item.stockCostMad());
            potentialSales = potentialSales.add(item.potentialSalesMad());
        }

        BigDecimal potentialProfit = potentialSales.subtract(totalCost);

        return new HBox(8,
                createMetricCard("Stock at cost", "%,.2f MAD".formatted(totalCost), false),
                createMetricCard("Potential sales", "%,.2f MAD".formatted(potentialSales), false),
                createMetricCard("Potential profit", "%,.2f MAD".formatted(potentialProfit),
                        potentialProfit.signum() < 0));
    }

    private void updateStockProfitRows(VBox rows, boolean showLosses) {
        rows.getChildren().clear();

        // Use the same scale for gains and losses.
        BigDecimal largestMagnitude = sampleStock.stream()
                .map(item -> item.potentialProfitMad().abs())
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        Comparator<StockItem> byProfit =
                Comparator.comparing(StockItem::potentialProfitMad);

        List<StockItem> rankedItems = sampleStock.stream()
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