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
                                    + "-fx-background-color: #087869; -fx-text-fill: white;")
                            .otherwise(segmentStyle
                                    + "-fx-background-color: #EDF2F5; -fx-text-fill: #172B40;"));
        }
        overview.setSelected(true);

        Label title = new Label("Potential profit by product");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox filterBar = new HBox(overview, gains, losses);
        VBox stockRows = new VBox(6);

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

        refresh.fire();
        return panel;
    }
    private void showProfitOverview(VBox rows, List<StockItem> items) {
        BigDecimal net = items.stream()
                .map(StockItem::potentialProfitMad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Label total = new Label(String.format(
                Locale.ENGLISH, "Net potential profit   %,.2f MAD", net));
        total.setStyle("-fx-font-weight: bold;");
        rows.getChildren().setAll(total);

        List<StockItem> products = items.stream()
                .filter(item -> item.potentialProfitMad().signum() != 0)
                .sorted(Comparator.comparing(StockItem::potentialProfitMad)
                        .thenComparing(StockItem::sku))
                .toList();

        if (products.isEmpty()) {
            rows.getChildren().add(new Label("No products with a potential gain or loss."));
            return;
        }

        NumberAxis amounts = new NumberAxis();
        amounts.setLabel("Potential profit (MAD)");

        CategoryAxis skus = new CategoryAxis();
        BarChart<Number, String> chart = new BarChart<>(amounts, skus);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setHorizontalGridLinesVisible(false);
        chart.setCategoryGap(10);

        XYChart.Series<Number, String> series = new XYChart.Series<>();

        for (StockItem item : products) {
            BigDecimal profit = item.potentialProfitMad();
            String label = String.format(
                    Locale.ENGLISH, "%s   %,.2f", item.sku(), profit);

            XYChart.Data<Number, String> point = new XYChart.Data<>(profit, label);
            StackPane bar = new StackPane();
            String color = profit.signum() < 0 ? "#B83B42" : "#087869";
            bar.setStyle("-fx-bar-fill: " + color + ";");
            point.setNode(bar);

            series.getData().add(point);
        }

        chart.getData().add(series);
        double height = 75 + 32.0 * products.size();
        chart.setMinHeight(height);
        chart.setPrefHeight(height);

        if (products.size() <= 5) {
            rows.getChildren().add(chart);
        } else {
            ScrollPane viewport = new ScrollPane(chart);
            viewport.setFitToWidth(true);
            viewport.setPrefViewportHeight(235);
            viewport.setMaxHeight(240);
            rows.getChildren().add(viewport);
        }
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

    private void updateStockProfitRows(
            VBox chartArea, List<StockItem> items, boolean showLosses) {

        Comparator<StockItem> byProfit =
                Comparator.comparing(StockItem::potentialProfitMad);

        Comparator<StockItem> order = showLosses ? byProfit : byProfit.reversed();

        List<StockItem> ranked = items.stream()
                .filter(item -> item.potentialProfitMad().signum() == (showLosses ? -1 : 1))
                .sorted(order.thenComparing(StockItem::sku))
                .limit(5)
                .toList();

        if (ranked.isEmpty()) {
            String message = showLosses
                    ? "No products with a potential loss."
                    : "No products with a potential gain.";

            chartArea.getChildren().setAll(new Label(message));
            return;
        }

        Canvas plot = new Canvas();
        double height = 42 + ranked.size() * 33;
        plot.setHeight(height);

        StackPane plotArea = new StackPane(plot);
        plotArea.setMinWidth(0);
        plotArea.setMinHeight(height);
        plotArea.setPrefHeight(height);

        plot.widthProperty().bind(plotArea.widthProperty());
        plot.widthProperty().addListener((observable, oldWidth, newWidth) ->
                drawProfitPlot(plot, ranked, showLosses));

        StringBuilder description = new StringBuilder("Potential profit in MAD.");
        for (StockItem item : ranked) {
            description.append(" ").append(item.sku()).append(": ")
                    .append(item.potentialProfitMad().toPlainString()).append(".");
        }
        plotArea.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        plotArea.setAccessibleText(description.toString());

        chartArea.getChildren().setAll(plotArea);
        drawProfitPlot(plot, ranked, showLosses);
    }

    private void drawProfitPlot(
            Canvas plot, List<StockItem> ranked, boolean showLosses) {

        double width = plot.getWidth();
        if (width < 100) {
            return;
        }

        GraphicsContext g = plot.getGraphicsContext2D();
        g.clearRect(0, 0, width, plot.getHeight());

        var font = javafx.scene.text.Font.font(12);
        g.setFont(font);

        // Reserve enough room for the product SKUs on the left.
        var measure = new javafx.scene.text.Text();
        measure.setFont(font);

        double longestSku = 0;
        for (StockItem item : ranked) {
            measure.setText(item.sku());
            longestSku = Math.max(longestSku, measure.getLayoutBounds().getWidth());
        }

        double left = Math.min(width * 0.34, longestSku + 12);
        double right = width - 26;
        double plotWidth = right - left;
        double bottom = 4 + ranked.size() * 33;

        // Choose readable tick intervals based on the largest visible bar.
        double largestAmount = ranked.get(0).potentialProfitMad().abs().doubleValue();
        int targetTicks = Math.max(2, Math.min(6, (int) (plotWidth / 66)));
        double rawStep = largestAmount / targetTicks;
        double power = Math.pow(10, Math.floor(Math.log10(rawStep)));

        double step;
        if (rawStep <= 2 * power) {
            step = 2 * power;
        } else if (rawStep <= 5 * power) {
            step = 5 * power;
        } else {
            step = 10 * power;
        }

        int ticks = (int) Math.ceil(largestAmount / step);
        double axisLimit = ticks * step;

        var numbers = new java.text.DecimalFormat(
                "#,##0.####",
                java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH));

        // For losses, zero sits at the right edge. For gains, it sits at the left.
        for (int i = 0; i <= ticks; i++) {
            double x = left + plotWidth * i / ticks;
            double value = (showLosses ? i - ticks : i) * step;

            g.setStroke(value == 0 ? Color.web("#506377") : Color.web("#DFE6EB"));
            g.strokeLine(x, 4, x, bottom);

            g.setFill(Color.web("#475569"));
            g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            g.fillText(numbers.format(value), x, bottom + 15);
        }

        for (int i = 0; i < ranked.size(); i++) {
            StockItem item = ranked.get(i);
            BigDecimal profit = item.potentialProfitMad();

            double y = 9 + i * 33;
            double length = plotWidth * profit.abs().doubleValue() / axisLimit;
            double x = showLosses ? right - length : left;

            g.setFill(Color.web("#172B40"));
            g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
            g.fillText(item.sku(), 0, y + 16, left - 8);

            g.setFill(Color.web(showLosses ? "#B83B42" : "#087869"));
            g.fillRect(x, y, length, 23);

            String amount = numbers.format(
                    profit.setScale(2, java.math.RoundingMode.HALF_UP));
            measure.setText(amount);

            // Short bars get an outside label so the amount stays readable.
            boolean fitsInside = length >= measure.getLayoutBounds().getWidth() + 12;
            boolean alignRight = fitsInside ? !showLosses : showLosses;
            double edge = showLosses ? x : x + length;

            g.setFill(fitsInside ? Color.WHITE : Color.web("#172B40"));
            g.setTextAlign(alignRight
                    ? javafx.scene.text.TextAlignment.RIGHT
                    : javafx.scene.text.TextAlignment.LEFT);
            g.fillText(amount, edge + (alignRight ? -5 : 5), y + 16);
        }

        g.setFill(Color.web("#475569"));
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.fillText("Potential profit (MAD)", (left + right) / 2, bottom + 34);
    }
}