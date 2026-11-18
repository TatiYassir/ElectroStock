package com.electrostock.frontend.view;

import java.time.LocalDate;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class PurchasesView extends VBox {

    public PurchasesView() {
        setPadding(new Insets(20));
        setSpacing(16);
        showList();
    }

    public void showList() {
        Label title = new Label("Purchases");
        title.getStyleClass().add("dashboard-title");

        Button newPurchase = new Button("New purchase");
        newPurchase.setOnAction(event -> showNewPurchase());

        ListView<String> purchases = new ListView<>();
        purchases.setPlaceholder(new Label("Purchase history is not connected yet."));

        getChildren().setAll(title, newPurchase, purchases);
    }

    public void showNewPurchase() {
        Label title = new Label("New purchase");
        title.getStyleClass().add("dashboard-title");

        ComboBox<String> supplier = new ComboBox<>();
        supplier.getItems().add("Test Supplier");
        supplier.setPromptText("Select a supplier");

        DatePicker purchaseDate = new DatePicker(LocalDate.now());

        ComboBox<String> currency = new ComboBox<>();
        currency.getItems().addAll("MAD", "EUR");
        currency.setValue("MAD");

        Label supplierLabel = new Label("Supplier");
        supplierLabel.setLabelFor(supplier);
        Label dateLabel = new Label("Purchase date");
        dateLabel.setLabelFor(purchaseDate);
        Label currencyLabel = new Label("Currency");
        currencyLabel.setLabelFor(currency);

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(12);
        form.addRow(0, supplierLabel, supplier);
        form.addRow(1, dateLabel, purchaseDate);
        form.addRow(2, currencyLabel, currency);

        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> showList());

        getChildren().setAll(title, form, cancel);
    }
}