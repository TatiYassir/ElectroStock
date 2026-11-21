package com.electrostock.frontend;

import com.electrostock.frontend.view.AppSidebar;
import com.electrostock.frontend.view.DashboardView;
import com.electrostock.frontend.view.ProductsView;
import com.electrostock.frontend.view.PurchasesView;
import com.electrostock.frontend.view.ReportsView;
import com.electrostock.frontend.view.SalesView;
import com.electrostock.frontend.view.StockView;
import com.electrostock.frontend.view.SuppliersView;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class ElectroStockApp extends Application {

    // Opening window size (outer size, including the title bar). The whole dashboard
    // is laid out to fit inside this without scrolling.
    private static final double WINDOW_WIDTH = 1700;
    private static final double WINDOW_HEIGHT = 912;
    private static final double WINDOW_MIN_WIDTH = 1100;
    private static final double WINDOW_MIN_HEIGHT = 640;

    private final BorderPane root = new BorderPane();
    private final AppSidebar sidebar = new AppSidebar(this::openSection);

    private DashboardView dashboard;
    private ProductsView products;
    private SalesView sales;
    private PurchasesView purchases;
    private SuppliersView suppliers;
    private StockView stock;
    private ReportsView reports;

    @Override
    public void start(Stage stage) {
        sales = new SalesView();
        purchases = new PurchasesView();
        dashboard = new DashboardView(this::openNewSale, this::openNewPurchase);

        root.setLeft(sidebar);

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(
                DashboardView.class.getResource("dashboard.css").toExternalForm());
        scene.getStylesheets().add(
                DashboardView.class.getResource("sidebar.css").toExternalForm());

        openSection("Dashboard");

        stage.setTitle("ElectroStock");
        stage.setScene(scene);
        var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(WINDOW_MIN_WIDTH, screen.getWidth()));
        stage.setMinHeight(Math.min(WINDOW_MIN_HEIGHT, screen.getHeight()));
        // Never larger than the usable screen, e.g. on a smaller monitor.
        stage.setWidth(Math.min(WINDOW_WIDTH, screen.getWidth()));
        stage.setHeight(Math.min(WINDOW_HEIGHT, screen.getHeight()));
        stage.centerOnScreen();
        stage.show();
    }

    private void openSection(String page) {
        switch (page) {
            case "Dashboard" -> showPage(page, dashboard);

            case "Products" -> {
                if (products == null) products = new ProductsView();
                showPage(page, products);
                products.onOpen();
            }
            case "Sales" -> {
                sales.showList();
                showPage(page, sales);
            }
            case "Purchases" -> {
                purchases.showList();
                showPage(page, purchases);
            }
            case "Stock" -> {
                if (stock == null) stock = new StockView();
                showPage(page, stock);
                stock.onOpen();
            }
            case "Suppliers" -> {
                if (suppliers == null) suppliers = new SuppliersView();
                showPage(page, suppliers);
                suppliers.onOpen();
            }

            case "Reports" -> {
                if (reports == null) reports = new ReportsView();
                showPage(page, reports);
                reports.onOpen();
            }

            default -> showPage(page, new Label(page));
        }
    }

    private void openNewSale() {
        sales.showNewSale();
        showPage("Sales", sales);
    }

    private void openNewPurchase() {
        purchases.showNewPurchase();
        showPage("Purchases", purchases);
    }

    private void showPage(String page, Node content) {
        root.setCenter(content);
        sidebar.setCurrent(page);
    }

    public static void main(String[] args) {
        launch(args);
    }
}