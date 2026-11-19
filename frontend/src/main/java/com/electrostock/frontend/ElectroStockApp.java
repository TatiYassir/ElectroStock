package com.electrostock.frontend;

import com.electrostock.frontend.view.AppSidebar;
import com.electrostock.frontend.view.DashboardView;
import com.electrostock.frontend.view.ProductsView;
import com.electrostock.frontend.view.PurchasesView;
import com.electrostock.frontend.view.SalesView;
import com.electrostock.frontend.view.SuppliersView;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class ElectroStockApp extends Application {

    private final BorderPane root = new BorderPane();
    private final AppSidebar sidebar = new AppSidebar(this::openSection);

    private DashboardView dashboard;
    private ProductsView products;
    private SalesView sales;
    private PurchasesView purchases;
    private SuppliersView suppliers;

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
        stage.setWidth(Math.min(1560, screen.getWidth()));
        stage.setHeight(Math.min(940, screen.getHeight()));
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
            case "Suppliers" -> {
                if (suppliers == null) suppliers = new SuppliersView();
                showPage(page, suppliers);
                suppliers.onOpen();
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
