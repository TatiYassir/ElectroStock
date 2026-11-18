package com.electrostock.frontend;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import com.electrostock.frontend.view.DashboardView;
public class ElectroStockApp extends Application {

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        DashboardView dashboard = new DashboardView();
        root.setCenter(dashboard);
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(16));
        sidebar.setPrefWidth(180);

        Label appName = new Label("ElectroStock");
        sidebar.getChildren().add(appName);

        for (String page : new String[]{
                "Dashboard", "Products", "Purchases", "Sales",
                "Suppliers", "Stock", "Reports"}) {
            Button button = new Button(page);
            button.setOnAction(event -> {
                if ("Dashboard".equals(page)) {
                    root.setCenter(dashboard);
                } else {
                    root.setCenter(new Label(page));
                }
            });
            button.setMaxWidth(Double.MAX_VALUE);
            sidebar.getChildren().add(button);
        }

        root.setLeft(sidebar);
        Scene scene = new Scene(root, 1200, 760);
        stage.setTitle("ElectroStock");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}