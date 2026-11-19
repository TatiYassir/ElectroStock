package com.electrostock.frontend.view;

import static com.electrostock.frontend.view.SalesView.bar;
import static com.electrostock.frontend.view.SalesView.field;
import static com.electrostock.frontend.view.SalesView.heading;
import static com.electrostock.frontend.view.SalesView.money;
import static com.electrostock.frontend.view.SalesView.small;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.electrostock.frontend.api.InventoryReportClient;
import com.electrostock.frontend.api.ProductApiClient;
import com.electrostock.frontend.api.ProductApiClient.Product;
import com.electrostock.frontend.api.SaleApiClient;
import com.electrostock.frontend.api.SaleApiClient.Line;
import com.electrostock.frontend.api.SaleApiClient.Sale;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Sales-only dialogs for choosing products and the serial units that leave stock. */
final class SaleDialogs {
    record AddLine(long productId, int quantity, BigDecimal unitPrice) {}
    private record Choice(Product product, long onHand, long alreadyInOtherLines) {
        long remaining() { return Math.max(0, onHand - alreadyInOtherLines); }
    }

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.9999");
    private final SaleApiClient sales;
    private final ProductApiClient catalog = new ProductApiClient();
    private final InventoryReportClient inventory = new InventoryReportClient();

    SaleDialogs(SaleApiClient sales) {
        this.sales = sales;
    }

    Optional<AddLine> addLine(Window owner, Sale sale) {
        return lineDialog(owner, sale, null);
    }

    Optional<AddLine> editLine(Window owner, Sale sale, Line existing) {
        return lineDialog(owner, sale, existing);
    }

    private Optional<AddLine> lineDialog(Window owner, Sale sale, Line existing) {
        boolean editing = existing != null;
        ButtonType submitType = action(editing ? "Save changes" : "Add product");
        Dialog<AddLine> dialog = dialog(
                owner,
                editing ? "Edit line #" + existing.id() + " · " + existing.productName()
                        : "Add product to sale #" + sale.id(),
                submitType);
        var submit = dialog.getDialogPane().lookupButton(submitType);

        TextField search = new TextField();
        search.setPromptText("Filter by SKU or product name…");
        search.setDisable(editing);

        var choices = FXCollections.<Choice>observableArrayList();
        var matches = new FilteredList<>(choices);
        search.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue.strip().toLowerCase(Locale.ROOT);
            matches.setPredicate(item -> item.product().sku().toLowerCase(Locale.ROOT).contains(query)
                    || item.product().name().toLowerCase(Locale.ROOT).contains(query));
        });

        ListView<Choice> picker = new ListView<>(matches);
        picker.setAccessibleText("Matching products");
        picker.setPrefHeight(editing ? 86 : 190);
        picker.setDisable(editing);
        Label empty = new Label("Loading available products…");
        picker.setPlaceholder(empty);
        picker.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Choice value, boolean isEmpty) {
                super.updateItem(value, isEmpty);
                if (isEmpty || value == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                Product product = value.product();
                long rem = value.remaining();

                Label productLabel = heading(product.name());
                Label stockPill = new Label(rem == 0 ? "Out of stock" : rem + " available");
                stockPill.getStyleClass().addAll("sale-pill",
                        rem == 0 ? "sale-pill-out" : rem <= 3 ? "sale-pill-low" : "sale-pill-stock");

                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                HBox topRow = new HBox(8, productLabel, gap, stockPill);
                topRow.setAlignment(Pos.CENTER_LEFT);

                StringBuilder meta = new StringBuilder(product.sku());
                if ("MAD".equals(sale.currencyCode()) && product.sellingPriceMad() != null) {
                    meta.append("  ·  ").append(money(product.sellingPriceMad(), "MAD"));
                }
                if (product.tracksSerials()) {
                    meta.append("  ·  Serial tracked");
                }
                if (value.alreadyInOtherLines() > 0) {
                    meta.append("  ·  ").append(value.alreadyInOtherLines()).append(" already in draft");
                }

                setGraphic(new VBox(3, topRow, small(meta.toString())));
                setTooltip(new Tooltip(product.sku() + " · " + product.name()));
            }
        });

        TextField quantity = new TextField(editing ? String.valueOf(existing.quantity()) : "1");
        quantity.setPrefColumnCount(4);
        quantity.setAlignment(Pos.CENTER);

        Button minusQty = new Button("−");
        minusQty.getStyleClass().add("sale-qty-btn");
        Button plusQty = new Button("+");
        plusQty.getStyleClass().add("sale-qty-btn");

        HBox qtyControl = new HBox(4, minusQty, quantity, plusQty);
        qtyControl.setAlignment(Pos.CENTER_LEFT);

        TextField price = new TextField(editing
                ? existing.unitPriceCurrency().stripTrailingZeros().toPlainString() : "");
        price.setPromptText("Unit price in " + sale.currencyCode());
        price.setMaxWidth(Double.MAX_VALUE);
        VBox priceBox = field("Unit price (" + sale.currencyCode() + ")", price);
        HBox.setHgrow(priceBox, Priority.ALWAYS);

        Label note = small("Select a product to view available stock.");
        note.setWrapText(true);
        Label estimate = heading("Line total: —");
        Label issue = new Label();
        issue.getStyleClass().add("sale-dialog-error");
        issue.setWrapText(true);
        issue.managedProperty().bind(issue.textProperty().isNotEmpty());

        Label loadError = new Label();
        loadError.getStyleClass().add("sale-dialog-error");
        loadError.setWrapText(true);
        loadError.managedProperty().bind(loadError.textProperty().isNotEmpty());

        BigDecimal baseSaleTotal = editing
                ? sale.totalCurrency().subtract(existing.lineTotalCurrency())
                : sale.totalCurrency();

        Runnable validate = () -> {
            Choice choice = picker.getSelectionModel().getSelectedItem();
            int currentQty = parseQuantity(quantity.getText());
            minusQty.setDisable(choice == null || currentQty <= 1);
            plusQty.setDisable(choice == null || currentQty >= choice.remaining());

            String problem = lineProblem(choice, quantity.getText(), price.getText(), baseSaleTotal);
            submit.setDisable(problem != null);
            issue.setText(choice == null || problem == null ? "" : problem);
            estimate.setText(problem == null
                    ? "Line total: " + money(
                            parsePrice(price.getText()).multiply(BigDecimal.valueOf(currentQty)),
                            sale.currencyCode())
                    : "Line total: —");
        };

        minusQty.setOnAction(e -> {
            int cur = parseQuantity(quantity.getText());
            if (cur > 1) quantity.setText(String.valueOf(cur - 1));
        });
        plusQty.setOnAction(e -> {
            Choice choice = picker.getSelectionModel().getSelectedItem();
            int cur = parseQuantity(quantity.getText());
            if (choice != null && cur < choice.remaining()) {
                quantity.setText(String.valueOf(cur + 1));
            }
        });

        picker.setOnMouseClicked(event -> {
            if (!editing && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Choice choice = picker.getSelectionModel().getSelectedItem();
                if (choice != null && choice.remaining() > 0) {
                    quantity.requestFocus();
                    quantity.selectAll();
                }
            }
        });

        picker.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, choice) -> {
            if (!editing) {
                price.clear();
                quantity.setText("1");
            }
            if (choice == null) {
                note.setText("Select a product to view available stock.");
            } else {
                Product product = choice.product();
                if (!editing && "MAD".equals(sale.currencyCode()) && product.sellingPriceMad() != null) {
                    price.setText(product.sellingPriceMad().stripTrailingZeros().toPlainString());
                }
                note.setText(choice.onHand() + " on hand · " + choice.alreadyInOtherLines() + " in other draft lines"
                        + (product.tracksSerials() ? " · Serial numbers chosen at confirmation."
                        : !"MAD".equals(sale.currencyCode()) ? " · Enter unit price in " + sale.currencyCode() + "." : "."));
            }
            validate.run();
        });
        quantity.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        price.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        VBox content = new VBox(11);
        content.setPadding(new Insets(12, 0, 4, 0));
        if (!editing) {
            content.getChildren().add(field("Find a product", search));
        }
        content.getChildren().addAll(picker, note, bar(field("Quantity", qtyControl), priceBox), estimate, issue, loadError);

        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(pressed -> pressed == submitType
                ? new AddLine(picker.getSelectionModel().getSelectedItem().product().id(),
                        parseQuantity(quantity.getText()), parsePrice(price.getText()))
                : null);

        var activeProducts = catalog.products();
        var stock = inventory.fetchStockAvailability();
        activeProducts.thenCombine(stock, (products, rows) -> {
            var stockById = rows.stream().collect(Collectors.toMap(
                    InventoryReportClient.LowStockItem::productId,
                    InventoryReportClient.LowStockItem::stockQuantity));
            var inOtherLines = sale.items().stream()
                    .filter(line -> !editing || line.id() != existing.id())
                    .collect(Collectors.groupingBy(Line::productId, Collectors.summingLong(Line::quantity)));
            return products.stream()
                    .filter(p -> "ACTIVE".equals(p.status()) || (editing && p.id() == existing.productId()))
                    .filter(p -> stockById.containsKey(p.id()))
                    .map(p -> new Choice(p, stockById.get(p.id()), inOtherLines.getOrDefault(p.id(), 0L)))
                    .toList();
        }).whenComplete((rows, error) -> Platform.runLater(() -> {
            if (!dialog.isShowing()) return;
            if (error != null) {
                empty.setText("Could not load the product catalog and stock.");
                loadError.setText(SalesView.message(error));
            } else {
                choices.setAll(rows);
                empty.setText(rows.isEmpty() ? "No active products available." : "No products match your search.");
                if (editing) {
                    rows.stream().filter(c -> c.product().id() == existing.productId())
                            .findFirst().ifPresent(picker.getSelectionModel()::select);
                    quantity.requestFocus();
                    quantity.selectAll();
                } else {
                    search.requestFocus();
                }
            }
        }));

        return dialog.showAndWait();
    }

    Optional< Map< Long, List< String > > > confirm(Window owner, Sale sale) {
        ButtonType confirm = action("Confirm sale");
        Dialog< Map< Long, List< String > > > dialog = dialog(owner, "Confirm sale #" + sale.id(), confirm);
        
        var submit = dialog.getDialogPane().lookupButton(confirm);
        
        // ... the rest of your code remains exactly the same
        List<Line> tracked = sale.items().stream().filter(Line::tracksSerials).toList();

        Label note = small("Confirming deducts stock immediately. Mark the sale handed over only after the customer receives the items.");
        note.setWrapText(true);
        Label issue = new Label();
        issue.getStyleClass().add("sale-dialog-error");
        issue.setWrapText(true);
        issue.managedProperty().bind(issue.textProperty().isNotEmpty());

        VBox content = new VBox(11, heading("Order total:  " + money(sale.totalCurrency(), sale.currencyCode())), note);
        content.setPadding(new Insets(12, 0, 4, 0));

        Map<Long, ObservableSet<String>> selections = new LinkedHashMap<>();
        Map<Long, ListView<String>> selectors = new LinkedHashMap<>();
        Map<Long, FilteredList<String>> filteredLists = new LinkedHashMap<>();
        Map<Long, Label> statusLabels = new LinkedHashMap<>();
        Map<Long, CompletableFuture<List<String>>> dataRequests = new LinkedHashMap<>();
        var ready = new SimpleBooleanProperty(tracked.isEmpty());

        Runnable validate = () -> {
            String error = null;
            Set<String> seen = new LinkedHashSet<>();
            if (ready.get()) {
                for (Line line : tracked) {
                    var picked = selections.get(line.id());
                    Label badge = statusLabels.get(line.id());
                    boolean complete = picked.size() == line.quantity();
                    badge.setText(picked.size() + " / " + line.quantity() + " selected");
                    badge.getStyleClass().setAll("label", "sale-pill",
                            complete ? "sale-pill-stock" : "sale-pill-low");

                    if (!complete && error == null) {
                        error = "Select " + line.quantity() + " serial number(s) for "
                                + line.sku() + " (line #" + line.id() + ").";
                    }
                    for (String s : picked) {
                        if (!seen.add(s) && error == null) {
                            error = "Serial " + s + " cannot be assigned to more than one line.";
                        }
                    }
                }
            }
            selectors.values().forEach(ListView::refresh);
            submit.setDisable(!ready.get() || error != null);
            if (ready.get()) {
                issue.setText(error == null ? "" : error);
            }
        };

        if (!tracked.isEmpty()) {
            TextField serialSearch = new TextField();
            serialSearch.setPromptText("Filter available serial numbers…");

            VBox pickers = new VBox(12);
            for (Line line : tracked) {
                ObservableSet<String> chosen = FXCollections.observableSet(new LinkedHashSet<>());
                selections.put(line.id(), chosen);

                var rawItems = FXCollections.<String>observableArrayList();
                var filtered = new FilteredList<>(rawItems);
                filteredLists.put(line.id(), filtered);

                ListView<String> picker = new ListView<>(filtered);
                picker.setPrefHeight(Math.min(148, 36 + Math.max(2, line.quantity()) * 28.0));
                picker.setPlaceholder(new Label("Loading in-stock serials…"));
                selectors.put(line.id(), picker);

                picker.setCellFactory(ignored -> new ListCell<>() {
                    @Override protected void updateItem(String serial, boolean isEmpty) {
                        super.updateItem(serial, isEmpty);
                        if (isEmpty || serial == null) {
                            setGraphic(null);
                            setDisable(false);
                            return;
                        }
                        boolean checkedHere = chosen.contains(serial);
                        boolean takenElsewhere = !checkedHere && selections.entrySet().stream()
                                .anyMatch(e -> e.getKey() != line.id() && e.getValue().contains(serial));

                        CheckBox box = new CheckBox(serial);
                        box.setSelected(checkedHere);
                        box.setDisable(takenElsewhere);
                        box.setMouseTransparent(true);

                        HBox row = new HBox(8, box);
                        row.setAlignment(Pos.CENTER_LEFT);
                        if (takenElsewhere) {
                            Region sp = new Region();
                            HBox.setHgrow(sp, Priority.ALWAYS);
                            row.getChildren().addAll(sp, small("Used on another line"));
                        }
                        setDisable(takenElsewhere);
                        setGraphic(row);
                        setOnMouseClicked(e -> {
                            if (takenElsewhere) return;
                            if (chosen.contains(serial)) {
                                chosen.remove(serial);
                            } else if (chosen.size() < line.quantity()) {
                                chosen.add(serial);
                            }
                        });
                    }
                });

                chosen.addListener((SetChangeListener<String>) change -> validate.run());

                Label countPill = new Label("0 / " + line.quantity() + " selected");
                countPill.getStyleClass().addAll("sale-pill", "sale-pill-low");
                statusLabels.put(line.id(), countPill);

                Button autoPick = new Button("Auto-pick");
                autoPick.getStyleClass().add("sale-qty-btn");
                autoPick.setTooltip(new Tooltip("Automatically pick the next available serials"));
                autoPick.setOnAction(e -> {
                    for (String candidate : rawItems) {
                        if (chosen.size() >= line.quantity()) break;
                        boolean usedElsewhere = selections.values().stream().anyMatch(set -> set.contains(candidate));
                        if (!usedElsewhere) {
                            chosen.add(candidate);
                        }
                    }
                });

                Button clearPick = new Button("Clear");
                clearPick.getStyleClass().add("sale-qty-btn");
                clearPick.setOnAction(e -> chosen.clear());

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox header = new HBox(8,
                        heading(line.sku() + " · " + line.productName()),
                        spacer, countPill, autoPick, clearPick);
                header.setAlignment(Pos.CENTER_LEFT);

                pickers.getChildren().add(new VBox(5, header, picker));
                dataRequests.computeIfAbsent(line.productId(), sales::availableSerials);
            }

            serialSearch.textProperty().addListener((obs, oldVal, newVal) -> {
                String q = newVal.strip().toLowerCase(Locale.ROOT);
                filteredLists.values().forEach(fl ->
                        fl.setPredicate(s -> q.isEmpty() || s.toLowerCase(Locale.ROOT).contains(q)));
            });

            ScrollPane scroll = new ScrollPane(pickers);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setPrefViewportHeight(Math.min(380, tracked.size() * 175.0));

            content.getChildren().addAll(field("Serial unit assignment", serialSearch), scroll, issue);

            CompletableFuture.allOf(dataRequests.values().toArray(CompletableFuture<?>[]::new))
                    .whenComplete((value, error) -> Platform.runLater(() -> {
                        if (!dialog.isShowing()) return;
                        if (error != null) {
                            selectors.values().forEach(picker ->
                                    picker.setPlaceholder(new Label("Serials could not be loaded.")));
                            issue.setText(SalesView.message(error));
                            return;
                        }
                        for (Line line : tracked) {
                            var picker = selectors.get(line.id());
                            @SuppressWarnings("unchecked")
                            var raw = (javafx.collections.ObservableList<String>) filteredLists.get(line.id()).getSource();
                            raw.setAll(dataRequests.get(line.productId()).join());
                            picker.setPlaceholder(new Label("No in-stock serials available."));
                        }
                        ready.set(true);
                        validate.run();
                    }));
        }

        validate.run();
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(pressed -> {
            if (pressed != confirm) return null;
            Map<Long, List<String>> selected = new LinkedHashMap<>();
            selections.forEach((id, chosen) -> selected.put(id, new ArrayList<>(chosen)));
            return selected;
        });
        return dialog.showAndWait();
    }

    private static String lineProblem(Choice choice, String quantity, String price, BigDecimal baseSaleTotal) {
        if (choice == null) return "Select a product.";
        if (choice.remaining() == 0) return "No more stock is available for this product.";
        int count = parseQuantity(quantity);
        if (count < 1) return "Enter a positive whole quantity.";
        if (count > choice.remaining()) return "Only " + choice.remaining() + " unit(s) available.";
        BigDecimal unitPrice = parsePrice(price);
        if (unitPrice == null) return "Enter a non-negative price with up to 4 decimal places.";
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(count)).add(baseSaleTotal);
        if (total.compareTo(MAX_AMOUNT) > 0) return "The amount is too large for a sale.";
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
        dialog.setTitle("ElectroStock · Sales");
        dialog.setHeaderText(title);
        dialog.setResizable(true);
        var pane = dialog.getDialogPane();
        SalesView.applyStyles(pane);
        pane.getStyleClass().add("sales-dialog");
        pane.setPrefWidth(650);
        pane.getButtonTypes().setAll(ButtonType.CANCEL, action);
        pane.lookupButton(action).getStyleClass().add("sale-primary");
        return dialog;
    }
}