package com.electrostock.frontend;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.electrostock.frontend.view.DashboardView;
import com.electrostock.frontend.view.SalesView;
import com.electrostock.frontend.view.PurchasesView;

import javafx.application.Application;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ElectroStockApp extends Application {

    private static final PseudoClass CURRENT_PAGE =
            PseudoClass.getPseudoClass("current-page");

    private final Map<String, Button> navigationButtons = new LinkedHashMap<>();
    private final BorderPane root = new BorderPane();

    private DashboardView dashboard;
    private SalesView sales;
    private PurchasesView purchases;
    
    @Override
    public void start(Stage stage) {
        sales = new SalesView();
        purchases = new PurchasesView();
        dashboard = new DashboardView(this::openNewSale, this::openNewPurchase);

        VBox sidebar = new VBox(new Label("ElectroStock"));
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setPrefWidth(180);

        List<String> pages = List.of(
                "Dashboard", "Products", "Purchases",
                "Sales", "Suppliers", "Stock", "Reports");

        for (String page : pages) {
            Button button = new Button(page);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> openSection(page));

            navigationButtons.put(page, button);
            sidebar.getChildren().add(button);
        }

        root.setLeft(sidebar);

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(
                DashboardView.class.getResource("dashboard.css").toExternalForm());

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

            case "Sales" -> {
                sales.showList();
                showPage(page, sales);
            }
            case "Purchases" -> {
                purchases.showList();
                showPage(page, purchases);
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

        navigationButtons.forEach((name, button) ->
                button.pseudoClassStateChanged(CURRENT_PAGE, name.equals(page)));
    }

    public static void main(String[] args) {
        launch(args);
    }
}