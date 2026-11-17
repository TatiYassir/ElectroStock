package com.electrostock.frontend;

import java.math.BigDecimal;

import com.electrostock.frontend.model.ProductRow;
import com.electrostock.frontend.api.ProductApiClient;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

public class ElectroStockApp extends Application {

    @Override
    public void start(Stage stage) {
        TableView<ProductRow> table = new TableView<>();

        TableColumn<ProductRow, String> skuColumn = new TableColumn<>("SKU");
        skuColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().sku()));
        skuColumn.setPrefWidth(150);

        TableColumn<ProductRow, String> nameColumn = new TableColumn<>("Product");
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().name()));
        nameColumn.setPrefWidth(350);

        TableColumn<ProductRow, BigDecimal> priceColumn =
                new TableColumn<>("Price (MAD)");
        priceColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().sellingPriceMad()));
        priceColumn.setPrefWidth(150);

        table.getColumns().add(skuColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(priceColumn);

        stage.setTitle("ElectroStock - Products");
        stage.setScene(new Scene(table, 800, 500));
        stage.show();
        loadProducts(table);
    }
    private void loadProducts(TableView<ProductRow> table) {
        Label message = new Label("Loading products...");
        table.setPlaceholder(message);

        ProductApiClient apiClient = new ProductApiClient();
        apiClient.fetchProducts().whenComplete((products, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        message.setText("Could not load products. See the Eclipse Console.");
                        error.printStackTrace();
                        return;
                    }

                    table.getItems().setAll(products);
                    message.setText("No products found.");
                }));
    }
    public static void main(String[] args) {
        launch(args);
    }
}