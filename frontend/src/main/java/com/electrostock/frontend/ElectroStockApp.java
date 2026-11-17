package com.electrostock.frontend;

import java.math.BigDecimal;

import com.electrostock.frontend.api.ProductApiClient;
import com.electrostock.frontend.model.ProductRow;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ElectroStockApp extends Application {

    // Reuse one API client for the first load and every refresh.
    private final ProductApiClient apiClient = new ProductApiClient();

    @Override
    public void start(Stage stage) {
        // Each row in this table represents one product.
        TableView<ProductRow> table = new TableView<>();
        table.setPlaceholder(new Label("No products to display."));

        // Read the SKU from each ProductRow and show it as text.
        TableColumn<ProductRow, String> skuColumn = new TableColumn<>("SKU");
        skuColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().sku()));
        skuColumn.setPrefWidth(150);

        // Read the product name from the same row.
        TableColumn<ProductRow, String> nameColumn = new TableColumn<>("Product");
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().name()));
        nameColumn.setPrefWidth(350);

        // Keep the price numeric so the column sorts prices as numbers.
        TableColumn<ProductRow, BigDecimal> priceColumn =
                new TableColumn<>("Price (MAD)");
        priceColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().sellingPriceMad()));
        priceColumn.setPrefWidth(150);

        // Add the columns in the order they should appear.
        table.getColumns().add(skuColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(priceColumn);

        Button refreshButton = new Button("Refresh");
        Label statusLabel = new Label();

        // Clicking Refresh calls the same method as the initial load.
        refreshButton.setOnAction(event ->
                loadProducts(table, refreshButton, statusLabel));

        // HBox places the button and message side by side.
        HBox toolbar = new HBox(8, refreshButton, statusLabel);

        // VBox puts the toolbar above the table.
        VBox root = new VBox(8, toolbar, table);

        // Let the table fill the remaining vertical space.
        VBox.setVgrow(table, Priority.ALWAYS);

        stage.setTitle("ElectroStock - Products");
        stage.setScene(new Scene(root, 800, 500));
        stage.show();

        // Load from Spring Boot as soon as the window opens.
        loadProducts(table, refreshButton, statusLabel);
    }

    private void loadProducts(
            TableView<ProductRow> table,
            Button refreshButton,
            Label statusLabel) {

        // Prevent another request while this one is still running.
        refreshButton.setDisable(true);
        statusLabel.setText("Loading products...");

        // Start the HTTP request without blocking the window.
        apiClient.fetchProducts().whenComplete((products, error) ->
                // Update the controls on the JavaFX UI thread.
                Platform.runLater(() -> {
                    // Allow another attempt whether this one succeeded or failed.
                    refreshButton.setDisable(false);

                    if (error != null) {
                        statusLabel.setText("Could not load products. See the Eclipse Console.");
                        error.printStackTrace();
                        return;
                    }

                    // Replace the old rows only after a successful response.
                    table.getItems().setAll(products);
                    statusLabel.setText(products.isEmpty()
                            ? "No products found."
                            : "Products loaded.");
                }));
    }

    public static void main(String[] args) {
        // Start JavaFX, which calls start(...) for us.
        launch(args);
    }
}