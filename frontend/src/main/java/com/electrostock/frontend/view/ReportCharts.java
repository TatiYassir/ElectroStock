package com.electrostock.frontend.view;

import com.electrostock.frontend.api.InventoryReportClient.SalesDay;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Builds report charts from the rows already loaded by ReportsView. */
final class ReportCharts {
    private static final int RANKING_LIMIT = 8;
    private static final int MAX_DAILY_POINTS = 366;
    private static final DateTimeFormatter AXIS_DATE = DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH);
    private static final DateTimeFormatter TOOLTIP_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    enum Tone { COBALT, EMERALD }

    @FunctionalInterface
    interface Model { Node build(); }
    private record Bar(String label, BigDecimal amount) {}

    private ReportCharts() {}

    static <T> Model rank(String title, List<T> rows, Function<T, String> label, Function<T, BigDecimal> value,
            boolean absoluteOrder) {
        return rank(title, rows, label, value, absoluteOrder, Tone.COBALT);
    }

    static <T> Model rank(String title, List<T> rows, Function<T, String> label, Function<T, BigDecimal> value,
            boolean absoluteOrder, Tone tone) {
        List<Bar> bars = rows.stream().filter(row -> value.apply(row) != null)
                .map(row -> new Bar(label.apply(row), value.apply(row)))
                .sorted(Comparator.<Bar, BigDecimal>comparing(b -> absoluteOrder ? b.amount().abs() : b.amount())
                        .reversed().thenComparing(Bar::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (bars.isEmpty()) return null;
        return () -> ranking(title, bars.subList(0, Math.min(RANKING_LIMIT, bars.size())), bars.size(), absoluteOrder, tone);
    }

    static Model daily(LocalDate from, LocalDate to, List<SalesDay> rows) {
        if (rows.isEmpty()) return null;
        long length = ChronoUnit.DAYS.between(from, to) + 1;
        if (length > MAX_DAILY_POINTS) {
            return () -> panel("Daily sales trend",
                    "Choose a range of 366 days or less for the chart. The table and CSV still cover your full range.",
                    null, null);
        }
        var days = new HashMap<LocalDate, SalesDay>();
        rows.forEach(row -> days.put(row.date(), row));
        return () -> trend(from, (int) length, days);
    }

    private static Node ranking(String title, List<Bar> bars, int total, boolean absoluteOrder, Tone tone) {
        NumberAxis x = new NumberAxis();
        x.setLabel("MAD");
        x.setForceZeroInRange(true);
        CategoryAxis y = new CategoryAxis();
        BarChart<Number, String> chart = new BarChart<>(x, y);
        chart.getStyleClass().add(tone == Tone.EMERALD ? "report-bar-emerald" : "report-bar-cobalt");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setAlternativeRowFillVisible(false);
        chart.setCategoryGap(11);
        chart.setBarGap(4);

        var series = new XYChart.Series<Number, String>();
        var used = new HashSet<String>();
        var categories = new ArrayList<String>();
        for (Bar bar : bars) {
            String axisLabel = uniqueLabel(bar.label(), used);
            categories.add(0, axisLabel);
            var point = new XYChart.Data<Number, String>(bar.amount(), axisLabel);
            series.getData().add(point);
        }
        y.setCategories(FXCollections.observableArrayList(categories));
        chart.getData().add(series);
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            decorate(series.getData().get(i).getNode(), bar.label() + "\n" + displayMad(bar.amount()),
                    bar.amount().signum() < 0);
        }

        Bar leader = bars.get(0);
        String callout = "Top: " + leader.label() + " (" + displayMad(leader.amount()) + ")";
        String details = "Showing top " + bars.size() + " of " + total + " groups"
                + (absoluteOrder ? " · ranked by absolute gross profit" : " · highest values first");
        return panel(title, details, callout, chart);
    }

    private static Node trend(LocalDate from, int length, HashMap<LocalDate, SalesDay> days) {
        NumberAxis x = length == 1 ? new NumberAxis(-0.5, 0.5, 0.5)
                : new NumberAxis(0, length - 1, Math.max(1, (length - 1 + 5) / 6));
        x.setMinorTickVisible(false);
        x.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) {
                double offset = value.doubleValue();
                return offset == Math.rint(offset) ? AXIS_DATE.format(from.plusDays(value.longValue())) : "";
            }
            @Override public Number fromString(String value) { throw new UnsupportedOperationException(); }
        });
        NumberAxis y = new NumberAxis();
        y.setLabel("MAD");
        y.setForceZeroInRange(true);

        var chart = new LineChart<Number, Number>(x, y);
        chart.getStyleClass().add("report-line-chart");
        chart.setAnimated(false);
        chart.setCreateSymbols(length <= 45);
        chart.setAlternativeColumnFillVisible(false);
        chart.setAlternativeRowFillVisible(false);

        var revenue = new XYChart.Series<Number, Number>();
        var grossProfit = new XYChart.Series<Number, Number>();
        revenue.setName("Revenue");
        grossProfit.setName("Gross profit");

        BigDecimal peakRevenue = BigDecimal.ZERO;
        LocalDate peakDate = null;

        for (int index = 0; index < length; index++) {
            LocalDate date = from.plusDays(index);
            SalesDay day = days.get(date);
            BigDecimal sales = day == null || day.revenueMad() == null ? BigDecimal.ZERO : day.revenueMad();
            BigDecimal profit = day == null || day.profitMad() == null ? BigDecimal.ZERO : day.profitMad();
            if (sales.compareTo(peakRevenue) > 0) {
                peakRevenue = sales;
                peakDate = date;
            }
            revenue.getData().add(new XYChart.Data<>(index, sales));
            grossProfit.getData().add(new XYChart.Data<>(index, profit));
        }

        chart.getData().add(revenue);
        chart.getData().add(grossProfit);

        if (chart.getCreateSymbols()) {
            for (var series : chart.getData()) {
                for (var point : series.getData()) {
                    LocalDate date = from.plusDays(point.getXValue().longValue());
                    decorate(point.getNode(), TOOLTIP_DATE.format(date) + "\n"
                            + series.getName() + " · " + displayMad((BigDecimal) point.getYValue()), false);
                }
            }
        }

        String callout = peakDate == null ? null
                : "Peak day: " + AXIS_DATE.format(peakDate) + " (" + displayMad(peakRevenue) + ")";
        return panel("Revenue & gross profit trend",
                "Calendar days · days without confirmed sales are plotted at zero", callout, chart);
    }

    private static void decorate(Node node, String text, boolean negative) {
        if (node == null) return;
        if (negative) node.getStyleClass().add("report-negative");
        Tooltip.install(node, new Tooltip(text));
    }

    private static String uniqueLabel(String full, HashSet<String> used) {
        int codePoints = full.codePointCount(0, full.length());
        String base = codePoints <= 25 ? full : full.substring(0, full.offsetByCodePoints(0, 24)) + "…";
        String result = base;
        for (int i = 2; !used.add(result); i++) result = base + " · " + i;
        return result;
    }

    private static String displayMad(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " MAD";
    }

    private static VBox panel(String title, String note, String callout, XYChart<?, ?> chart) {
        Label heading = new Label(title);
        heading.getStyleClass().add("report-chart-title");
        Label description = new Label(note);
        description.getStyleClass().add("report-muted");
        description.setWrapText(true);

        HBox headerRow = new HBox(10, new VBox(2, heading, description));
        headerRow.setAlignment(Pos.CENTER_LEFT);
        if (callout != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label pill = new Label(callout);
            pill.getStyleClass().add("report-chart-callout");
            headerRow.getChildren().addAll(spacer, pill);
        }

        VBox panel = new VBox(10, headerRow);
        panel.getStyleClass().add("report-chart");
        if (chart != null) {
            chart.setMinHeight(210);
            chart.setAccessibleText(title);
            panel.getChildren().add(chart);
            VBox.setVgrow(chart, Priority.ALWAYS);
        }
        return panel;
    }
}