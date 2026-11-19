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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Purchases-only dialogs for choosing products and entering incoming serial units. */
final class PurchaseDialogs {
    record AddLine(long productId, int quantity, BigDecimal unitPrice) {}
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.9999");
    private final ProductApiClient catalog = new ProductApiClient();

    Optional<AddLine> addLine(Window owner, Purchase purchase) {
        ButtonType add = action("Add to purchase");
        Dialog<AddLine> dialog = dialog(owner, "Add product to purchase #" + purchase.id(), add);
        var submit = dialog.getDialogPane().lookupButton(add);

        TextField search = new TextField();
        search.setPromptText("Search by SKU or product name…");

        var choices = FXCollections.<Product>observableArrayList();
        var matches = new FilteredList<>(choices);
        search.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue.strip().toLowerCase(Locale.ROOT);
            matches.setPredicate(item -> item.sku().toLowerCase(Locale.ROOT).contains(query)
                    || item.name().toLowerCase(Locale.ROOT).contains(query));
        });

        ListView<Product> picker = new ListView<>(matches);
        picker.setAccessibleText("Matching products");
        picker.setPrefHeight(205);
        Label empty = new Label("Loading available products…");
        picker.setPlaceholder(empty);
        picker.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Product product, boolean isEmpty) {
                super.updateItem(product, isEmpty);
                if (isEmpty || product == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                Label productLabel = heading(product.name());
                Label skuBadge = new Label(product.sku());
                skuBadge.getStyleClass().add("purchase-sku-tag");

                HBox metaRow = new HBox(8, skuBadge);
                metaRow.setAlignment(Pos.CENTER_LEFT);
                if (product.tracksSerials()) {
                    Label serialTag = new Label("Serial tracked");
                    serialTag.getStyleClass().add("purchase-meta-tag");
                    metaRow.getChildren().add(serialTag);
                }

                setGraphic(new VBox(5, productLabel, metaRow));
                setTooltip(new Tooltip(product.sku() + " · " + product.name()));
            }
        });

        Label selectedTitle = heading("No product selected");
        Label selectedMeta = small("Choose an item from the catalog list above to configure quantity and supplier cost.");
        selectedMeta.setWrapText(true);
        VBox selectionStrip = new VBox(3, selectedTitle, selectedMeta);
        selectionStrip.getStyleClass().add("purchase-selection-strip");

        TextField quantity = new TextField("1");
        quantity.setPrefColumnCount(4);
        quantity.setPrefHeight(36);
        quantity.setAlignment(Pos.CENTER);

        Button minusQty = new Button("−");
        minusQty.getStyleClass().add("purchase-qty-btn");
        minusQty.setPrefSize(36, 36);
        Button plusQty = new Button("+");
        plusQty.getStyleClass().add("purchase-qty-btn");
        plusQty.setPrefSize(36, 36);

        HBox qtyControl = new HBox(6, minusQty, quantity, plusQty);
        qtyControl.setAlignment(Pos.CENTER_LEFT);

        TextField price = new TextField();
        price.setPromptText("0.00");
        price.setPrefHeight(36);
        price.setMaxWidth(Double.MAX_VALUE);
        VBox priceBox = field("Unit cost (" + purchase.currencyCode() + ")", price);
        HBox.setHgrow(priceBox, Priority.ALWAYS);

        Label breakdownLabel = small("Enter quantity and unit cost");
        Label estimateValue = new Label("—");
        estimateValue.getStyleClass().add("purchase-total-highlight");

        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        HBox summaryBox = new HBox(10,
                new VBox(2, small("LINE TOTAL"), breakdownLabel),
                totalSpacer,
                estimateValue);
        summaryBox.getStyleClass().add("purchase-line-summary-box");
        summaryBox.setAlignment(Pos.CENTER_LEFT);

        Label issue = new Label();
        issue.getStyleClass().add("purchase-dialog-error");
        issue.setWrapText(true);
        issue.managedProperty().bind(issue.textProperty().isNotEmpty());

        Label loadError = new Label();
        loadError.getStyleClass().add("purchase-dialog-error");
        loadError.setWrapText(true);
        loadError.managedProperty().bind(loadError.textProperty().isNotEmpty());

        Runnable validate = () -> {
            Product choice = picker.getSelectionModel().getSelectedItem();
            int currentQty = parseQuantity(quantity.getText());
            minusQty.setDisable(choice == null || currentQty <= 1);
            plusQty.setDisable(choice == null);

            String problem = lineProblem(choice, quantity.getText(), price.getText(), purchase.totalCurrency());
            submit.setDisable(problem != null);
            issue.setText(choice == null || problem == null ? "" : problem);
            if (problem == null) {
                BigDecimal unit = parsePrice(price.getText());
                BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(currentQty));
                breakdownLabel.setText(currentQty + " × " + money(unit, purchase.currencyCode()));
                estimateValue.setText(money(lineTotal, purchase.currencyCode()));
            } else {
                breakdownLabel.setText(choice == null ? "Select a product first" : "Check quantity & unit cost");
                estimateValue.setText("—");
            }
        };

        minusQty.setOnAction(e -> {
            int cur = parseQuantity(quantity.getText());
            if (cur > 1) quantity.setText(String.valueOf(cur - 1));
        });
        plusQty.setOnAction(e -> {
            int cur = parseQuantity(quantity.getText());
            quantity.setText(String.valueOf(Math.max(1, cur + 1)));
        });

        picker.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Product choice = picker.getSelectionModel().getSelectedItem();
                if (choice != null) {
                    quantity.requestFocus();
                    quantity.selectAll();
                }
            }
        });

        picker.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, choice) -> {
            price.clear();
            quantity.setText("1");
            if (choice == null) {
                selectedTitle.setText("No product selected");
                selectedMeta.setText("Choose an item from the catalog list above to configure quantity and supplier cost.");
            } else {
                selectedTitle.setText(choice.name() + " (" + choice.sku() + ")");
                selectedMeta.setText("Enter the supplier's unit cost in " + purchase.currencyCode()
                        + (choice.tracksSerials() ? "  ·  Received serials entered at confirmation" : ""));
            }
            validate.run();
        });
        quantity.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        price.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        VBox content = new VBox(12,
                field("Find a product", search),
                picker,
                selectionStrip,
                bar(field("Quantity", qtyControl), priceBox),
                summaryBox,
                issue,
                loadError);
        content.setPadding(new Insets(4, 0, 4, 0));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(pressed -> pressed == add
                ? new AddLine(picker.getSelectionModel().getSelectedItem().id(),
                        parseQuantity(quantity.getText()), parsePrice(price.getText()))
                : null);

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
                        search.requestFocus();
                    }
                }));

        return dialog.showAndWait();
    }

    Optional< Map< Long, List< String > > > confirm(Window owner, Purchase purchase) {
        ButtonType confirm = action("Confirm receipt");
        Dialog< Map< Long, List< String > > > dialog = dialog(owner,
                "Confirm purchase #" + purchase.id(), confirm);
        var submit = dialog.getDialogPane().lookupButton(confirm);
        List<Line> tracked = purchase.items().stream().filter(Line::tracksSerials).toList();

        int lineCount = purchase.items().size();
        int totalUnits = purchase.items().stream().mapToInt(Line::quantity).sum();

        Label totalEyebrow = new Label("PURCHASE TOTAL");
        totalEyebrow.getStyleClass().add("purchase-eyebrow");
        Label heroAmount = new Label(money(purchase.totalCurrency(), purchase.currencyCode()));
        heroAmount.getStyleClass().add("purchase-confirm-amount");

        Label orderMeta = new Label(lineCount + " item(s)  ·  " + totalUnits + " unit(s)");
        orderMeta.getStyleClass().add("purchase-pill-neutral");
        Label dateMeta = small("Purchase date: " + PurchasesView.formatDate(purchase.purchaseDate()));

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);
        VBox rightMeta = new VBox(5, orderMeta, dateMeta);
        rightMeta.setAlignment(Pos.CENTER_RIGHT);

        HBox heroCard = new HBox(12,
                new VBox(3, totalEyebrow, heroAmount),
                heroSpacer,
                rightMeta);
        heroCard.getStyleClass().add("purchase-confirm-hero");
        heroCard.setAlignment(Pos.CENTER_LEFT);

        VBox stockNotice = new VBox(3,
                heading("Inventory receipt notice"),
                small("Confirm only after the goods have arrived. This immediately adds the quantities to available stock."));
        stockNotice.getStyleClass().add("purchase-callout-amber");
        stockNotice.getChildren().forEach(node -> {
            if (node instanceof Label lbl) lbl.setWrapText(true);
        });

        Label issue = new Label();
        issue.getStyleClass().add("purchase-dialog-error");
        issue.setWrapText(true);
        issue.managedProperty().bind(issue.textProperty().isNotEmpty());

        VBox content = new VBox(12, heroCard, stockNotice);
        content.setPadding(new Insets(4, 0, 4, 0));

        Map<Long, TextArea> serialFields = new LinkedHashMap<>();
        Map<Long, Label> statusLabels = new LinkedHashMap<>();

        Runnable validate = () -> {
            String error = null;
            var seen = new HashSet<String>();
            for (Line line : tracked) {
                var entered = parseSerials(serialFields.get(line.id()).getText());
                Label badge = statusLabels.get(line.id());
                boolean complete = entered.size() == line.quantity();
                boolean over = entered.size() > line.quantity();
                badge.setText(entered.size() + " / " + line.quantity() + " entered");
                badge.getStyleClass().setAll("label", "purchase-pill",
                        complete ? "purchase-pill-stock" : over ? "purchase-pill-out" : "purchase-pill-low");

                if (error == null && !complete) {
                    error = "Enter exactly " + line.quantity() + " serial number(s) for "
                            + line.sku() + " (line #" + line.id() + ").";
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
            Label hint = small("Type or paste one incoming serial number per line for each serial-tracked item.");
            hint.setWrapText(true);
            VBox fields = new VBox(12);
            for (Line line : tracked) {
                TextArea input = new TextArea();
                input.setPrefRowCount(Math.min(5, Math.max(2, line.quantity())));
                input.setPromptText("One serial number per line…");
                input.setWrapText(false);
                input.setMaxWidth(Double.MAX_VALUE);

                Label countPill = new Label("0 / " + line.quantity() + " entered");
                countPill.getStyleClass().addAll("purchase-pill", "purchase-pill-low");

                Button clearBtn = new Button("Clear");
                clearBtn.getStyleClass().add("purchase-qty-btn");
                clearBtn.setOnAction(e -> input.clear());

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox header = new HBox(8,
                        heading(line.sku() + " · " + line.productName()),
                        spacer, countPill, clearBtn);
                header.setAlignment(Pos.CENTER_LEFT);

                serialFields.put(line.id(), input);
                statusLabels.put(line.id(), countPill);
                input.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
                fields.getChildren().add(new VBox(5, header, input));
            }
            ScrollPane scroll = new ScrollPane(fields);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setPrefViewportHeight(Math.min(360, Math.max(135, tracked.size() * 155.0)));
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
        pane.setPrefWidth(640);
        pane.getButtonTypes().setAll(ButtonType.CANCEL, action);
        Node confirmBtn = pane.lookupButton(action);
        if (confirmBtn != null) confirmBtn.getStyleClass().add("purchase-primary");
        Node cancelBtn = pane.lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.getStyleClass().add("purchase-secondary");
        return dialog;
    }
}