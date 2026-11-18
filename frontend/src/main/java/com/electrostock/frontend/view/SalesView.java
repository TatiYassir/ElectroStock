package com.electrostock.frontend.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;


public class SalesView extends VBox {
	private record CatalogProduct(String sku, String name) {
	    @Override
	    public String toString() {
	        return sku + " — " + name;
	    }
	}

	private final List<CatalogProduct> sampleCatalog = List.of(
	        new CatalogProduct("MON-001", "24-inch Monitor"),
	        new CatalogProduct("MON-002", "24-inch Monitor")
	);
    public SalesView() {
        setPadding(new Insets(20));
        setSpacing(16);
        showList();
    }

    public void showList() {
        Label title = new Label("Sales");
        title.getStyleClass().add("dashboard-title");

        Button newSale = new Button("New sale");
        newSale.setOnAction(event -> showNewSale());

        ListView<String> salesList = new ListView<>();
        salesList.setPlaceholder(new Label("Sales history is not connected yet."));

        getChildren().setAll(title, newSale, salesList);
    }

    public void showNewSale() {
        Label title = new Label("New sale");
        title.getStyleClass().add("dashboard-title");

        DatePicker saleDate = new DatePicker(LocalDate.now());

        ComboBox<String> currency = new ComboBox<>();
        currency.getItems().addAll("MAD", "EUR");
        currency.setValue("MAD");
        currency.setMaxWidth(Double.MAX_VALUE);

        Label dateLabel = new Label("Sale date");
        dateLabel.setLabelFor(saleDate);

        Label currencyLabel = new Label("Currency");
        currencyLabel.setLabelFor(currency);

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(12);
        form.addRow(0, dateLabel, saleDate);
        form.addRow(1, currencyLabel, currency);
        TextField search = new TextField();
        search.setPromptText("Search by SKU or product name");

        Label searchLabel = new Label("Find a product");
        searchLabel.setLabelFor(search);

        ListView<CatalogProduct> matches = new ListView<>();
        matches.setPrefHeight(100);
        matches.setPlaceholder(new Label("No matching products"));
        matches.getItems().setAll(sampleCatalog);

        search.textProperty().addListener((observable, oldText, newText) -> {
            String query = newText.strip().toLowerCase(Locale.ROOT);

            List<CatalogProduct> filtered = sampleCatalog.stream()
                    .filter(product ->
                            product.sku().toLowerCase(Locale.ROOT).contains(query)
                            || product.name().toLowerCase(Locale.ROOT).contains(query))
                    .toList();

            matches.getItems().setAll(filtered);
        });

        VBox productPicker = new VBox(6, searchLabel, search, matches);
        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> showList());

        getChildren().setAll(title, form, productPicker, cancel);
    }
}