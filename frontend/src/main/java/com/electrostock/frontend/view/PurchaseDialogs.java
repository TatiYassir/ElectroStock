package com.electrostock.frontend.view;

import static com.electrostock.frontend.view.PurchasesView.bar;
import static com.electrostock.frontend.view.PurchasesView.field;
import static com.electrostock.frontend.view.PurchasesView.heading;
import static com.electrostock.frontend.view.PurchasesView.money;
import static com.electrostock.frontend.view.PurchasesView.small;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.electrostock.frontend.api.ProductApiClient;
import com.electrostock.frontend.api.ProductApiClient.Product;
import com.electrostock.frontend.api.PurchaseApiClient.Line;
import com.electrostock.frontend.api.PurchaseApiClient.Purchase;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Purchases-only dialogs for choosing products and the serial units that leave stock. */
final class PurchaseDialogs {
    record AddLine(long productId, int quantity, BigDecimal unitPrice) {}
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.9999");
    private final ProductApiClient catalog = new ProductApiClient();

    Optional<AddLine> addLine(Window owner, Purchase purchase) {
        ButtonType add = action("Add product");
        Dialog<AddLine> dialog = dialog(owner, "Add product to purchase #" + purchase.id(), add);
        var submit = dialog.getDialogPane().lookupButton(add);

        TextField search = new TextField();
        search.setPromptText("Search by SKU or product name");
        var choices = FXCollections.<Product>observableArrayList();
        var matches = new FilteredList<>(choices);
        search.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue.strip().toLowerCase(Locale.ROOT);
            matches.setPredicate(item -> item.sku().toLowerCase(Locale.ROOT).contains(query)
                    || item.name().toLowerCase(Locale.ROOT).contains(query));
        });
        ListView<Product> picker = new ListView<>(matches);
        picker.setAccessibleText("Matching products");
        picker.setPrefHeight(170);
        Label empty = new Label("Loading available products…");
        picker.setPlaceholder(empty);
        picker.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Product value, boolean isEmpty) {
                super.updateItem(value, isEmpty);
                if (isEmpty || value == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                Product product = value;
                Label productLabel = heading(product.name());
                Label info = small(product.sku()
                        + (product.tracksSerials() ? "  ·  Serial tracked" : ""));
                setGraphic(new VBox(2, productLabel, info));
                setTooltip(new Tooltip(product.sku() + " · " + product.name()));
            }
        });

        TextField quantity = new TextField("1");
        quantity.setPrefColumnCount(5);
        TextField price = new TextField();
        price.setPromptText("Unit cost in " + purchase.currencyCode());
        price.setMaxWidth(Double.MAX_VALUE);
        VBox priceBox = field("Unit cost (" + purchase.currencyCode() + ")", price);
        HBox.setHgrow(priceBox, Priority.ALWAYS);
        Label note = small("Choose a product from the live catalog.");
        note.setWrapText(true);
        Label estimate = heading("Line total: —");
        Label issue = new Label();
        issue.getStyleClass().add("purchase-dialog-error");
        issue.setWrapText(true);
        Label loadError = new Label();
        loadError.getStyleClass().add("purchase-dialog-error");
        loadError.setWrapText(true);
        loadError.managedProperty().bind(loadError.textProperty().isNotEmpty());

        Runnable validate = () -> {
            Product choice = picker.getSelectionModel().getSelectedItem();
            String problem = lineProblem(choice, quantity.getText(), price.getText(), purchase.totalCurrency());
            submit.setDisable(problem != null);
            issue.setText(choice == null || problem == null ? "" : problem);
            estimate.setText(problem == null ? "Line total: " + money(
                    parsePrice(price.getText()).multiply(BigDecimal.valueOf(parseQuantity(quantity.getText()))),
                    purchase.currencyCode()) : "Line total: —");
        };

        picker.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, choice) -> {
            price.clear();
            quantity.setText("1");
            if (choice == null) {
                note.setText("Choose a product from the live catalog.");
            } else {
                note.setText("Enter the supplier's unit cost in " + purchase.currencyCode() + "."
                        + (choice.tracksSerials() ? " You will enter the received serials at confirmation." : ""));
            }
            validate.run();
        });
        quantity.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        price.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();
        VBox content = new VBox(10, field("Find a product", search), picker, note,
                bar(field("Quantity", quantity), priceBox), estimate, issue, loadError);
        content.setPadding(new Insets(12, 0, 4, 0));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(pressed -> pressed == add
                ? new AddLine(picker.getSelectionModel().getSelectedItem().id(),
                        parseQuantity(quantity.getText()), parsePrice(price.getText())) : null);

        catalog.products()
                .thenApply(products -> products.stream().filter(p -> "ACTIVE".equals(p.status())).toList())
                .whenComplete((rows, error) -> Platform.runLater(() -> {
            if (!dialog.isShowing()) return;
            if (error != null) {
                empty.setText("Could not load the product catalog.");
                loadError.setText(PurchasesView.message(error));
            } else {
                choices.setAll(rows);
                empty.setText(rows.isEmpty() ? "No active products available." : "No products match your search.");
            }
        }));

        return dialog.showAndWait();
    }

    Optional<Map<Long, List<String>>> confirm(Window owner, Purchase purchase) {
        ButtonType confirm = action("Confirm receipt");
        Dialog<Map<Long, List<String>>> dialog = dialog(owner,
                "Confirm purchase #" + purchase.id(), confirm);
        var submit = dialog.getDialogPane().lookupButton(confirm);
        List<Line> tracked = purchase.items().stream().filter(Line::tracksSerials).toList();
        Label note = small("Confirm only after the goods arrive. This adds the quantities to stock.");
        note.setWrapText(true);
        Label issue = new Label();
        issue.getStyleClass().add("purchase-dialog-error");
        issue.setWrapText(true);
        VBox content = new VBox(10, heading("Total  " + money(purchase.totalCurrency(), purchase.currencyCode())), note);
        content.setPadding(new Insets(12, 0, 4, 0));
        Map<Long, TextArea> serialFields = new LinkedHashMap<>();
        Map<Long, Label> counts = new LinkedHashMap<>();

        Runnable validate = () -> {
            String error = null;
            var seen = new HashSet<String>();
            for (Line line : tracked) {
                var entered = parseSerials(serialFields.get(line.id()).getText());
                counts.get(line.id()).setText(entered.size() + " / " + line.quantity() + " serials entered");
                if (error == null && entered.size() != line.quantity()) {
                    error = "Enter exactly " + line.quantity() + " serial(s) for line #" + line.id() + ".";
                }
                for (String serial : entered) {
                    if (serial.length() > 150 && error == null) {
                        error = "Serial numbers can contain at most 150 characters.";
                    }
                    if (!seen.add(serial.toLowerCase(Locale.ROOT)) && error == null) {
                        error = "Duplicate serial number: " + serial;
                    }
                }
            }
            submit.setDisable(error != null);
            issue.setText(error == null ? "" : error);
        };

        if (!tracked.isEmpty()) {
            Label hint = small("Type or paste one incoming serial number per line.");
            hint.setWrapText(true);
            VBox fields = new VBox(10);
            for (Line line : tracked) {
                TextArea input = new TextArea();
                input.setPrefRowCount(Math.min(5, Math.max(2, line.quantity())));
                input.setPromptText("One serial per line");
                input.setWrapText(false);
                input.setMaxWidth(Double.MAX_VALUE);
                Label count = small("");
                serialFields.put(line.id(), input);
                counts.put(line.id(), count);
                input.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
                fields.getChildren().add(new VBox(3, field(line.sku() + " · " + line.productName()
                        + "  |  Line #" + line.id(), input), count));
            }
            ScrollPane scroll = new ScrollPane(fields);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setPrefViewportHeight(Math.min(350, Math.max(125, tracked.size() * 145.0)));
            content.getChildren().addAll(hint, scroll, issue);
        }
        validate.run();
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(pressed -> {
            if (pressed != confirm) return null;
            Map<Long, List<String>> entered = new LinkedHashMap<>();
            serialFields.forEach((id, input) -> entered.put(id, parseSerials(input.getText())));
            return entered;
        });
        return dialog.showAndWait();
    }

    private static List<String> parseSerials(String text) {
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private static String lineProblem(Product choice, String quantity, String price, BigDecimal purchaseTotal) {
        if (choice == null) return "Select a product.";
        int count = parseQuantity(quantity);
        if (count < 1) return "Enter a positive whole quantity.";
        BigDecimal unitPrice = parsePrice(price);
        if (unitPrice == null) return "Enter a non-negative unit cost with up to 4 decimal places.";
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(count)).add(purchaseTotal);
        if (total.compareTo(MAX_AMOUNT) > 0) return "The amount is too large for a purchase.";
        return null;
    }

    private static int parseQuantity(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal parsePrice(String text) {
        String normalized = text.strip().replace(',', '.');
        return normalized.matches("\\d{1,15}(\\.\\d{1,4})?")
                ? new BigDecimal(normalized) : null;
    }

    private static ButtonType action(String caption) {
        return new ButtonType(caption, ButtonBar.ButtonData.OK_DONE);
    }

    private static <T> Dialog<T> dialog(Window owner, String title, ButtonType action) {
        Dialog<T> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("ElectroStock · Purchases");
        dialog.setHeaderText(title);
        dialog.setResizable(true);
        var pane = dialog.getDialogPane();
        PurchasesView.applyStyles(pane);
        pane.getStyleClass().add("purchases-dialog");
        pane.setPrefWidth(620);
        pane.getButtonTypes().setAll(ButtonType.CANCEL, action);
        pane.lookupButton(action).getStyleClass().add("purchase-primary");
        return dialog;
    }
}
