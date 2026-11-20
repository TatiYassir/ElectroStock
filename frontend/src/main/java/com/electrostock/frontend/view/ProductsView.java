package com.electrostock.frontend.view;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

import com.electrostock.frontend.api.InventoryReportClient;
import com.electrostock.frontend.api.ProductApiClient;
import com.electrostock.frontend.api.ProductApiClient.Category;
import com.electrostock.frontend.api.ProductApiClient.Product;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ProductsView extends VBox {
    private static final String SEARCH_ICON = "M11 19A8 8 0 1 0 11 3A8 8 0 1 0 11 19 M21 21L16.65 16.65";
    private static final PseudoClass FOCUS_WITHIN = PseudoClass.getPseudoClass("focus-within");

    private final ProductApiClient api = new ProductApiClient();
    private final InventoryReportClient reports = new InventoryReportClient();
    private final List<Product> catalogue = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();
    private final Map<Long, Long> quantities = new HashMap<>();
    private final DecimalFormat money = new DecimalFormat("#,##0.00##",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    private final TextField search = new TextField();
    private final Category allCategories = new Category(-1, "All categories", null);
    private final ComboBox<Category> category = new ComboBox<>();
    private final ComboBox<String> level = choices("All stock levels", "In stock", "Low stock", "Out of stock");
    private final ComboBox<String> status = choices("Active", "Inactive", "All statuses");
    private final TableView<Product> table = new TableView<>();
    private final Label count = new Label();
    private final Label notice = new Label();
    private final Label statTotal = new Label("0");
    private final Label statIn = new Label("0");
    private final Label statLow = new Label("0");
    private final Label statOut = new Label("0");
    private final VBox inspector = new VBox(14);
    private final Button add = new Button("+  New product");
    private final Button refresh = new Button("Refresh");
    private final Button manageCategories = new Button("Categories");
    private final HBox toolbar = new HBox(10);
    private boolean busy;
    private boolean editing;

    public ProductsView() {
        getStyleClass().add("products-page");
        getStylesheets().add(ProductsView.class.getResource("products.css").toExternalForm());
        add.getStyleClass().add("product-primary");
        add.setOnAction(e -> form(null));
        refresh.setOnAction(e -> onOpen());
        manageCategories.setOnAction(e -> manageCategories());
        category.getItems().add(allCategories);
        category.setValue(allCategories);
        makeTable();

        // toolbar with an icon search box
        search.setPromptText("Search by name or SKU");
        HBox searchBox = new HBox(8, ProductIcons.plain(SEARCH_ICON, "#64748b", 18), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        search.focusedProperty().addListener((o, a, focused) -> searchBox.pseudoClassStateChanged(FOCUS_WITHIN, focused));
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(searchBox, category, level, status);

        // header
        VBox titles = new VBox(2, styled("Products", "product-title"),
                styled("Manage your catalogue, prices and stock levels", "product-subtitle"));
        HBox header = line(titles, spacer(), refresh, manageCategories, add);
        header.setSpacing(10);

        // summary cards
        HBox stats = new HBox(14, statCard("Active products", "total", statTotal),
                statCard("In stock", "in", statIn), statCard("Low stock", "low", statLow),
                statCard("Out of stock", "out", statOut));

        // list + inspector
        count.getStyleClass().add("product-chip");
        VBox list = new VBox(14, line(styled("Product catalogue", "product-heading"), spacer(), count), table);
        list.getStyleClass().add("product-card");
        list.setMinWidth(0);
        VBox.setVgrow(table, Priority.ALWAYS);
        ScrollPane details = new ScrollPane(inspector);
        details.setFitToWidth(true);
        details.getStyleClass().add("product-detail-scroll");
        details.setMinWidth(0);
        inspector.getStyleClass().add("product-card");
        GridPane body = new GridPane();
        body.setHgap(14);
        for (double percent : new double[]{62, 38}) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(percent);
            body.getColumnConstraints().add(col);
        }
        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        body.getRowConstraints().add(row);
        body.addRow(0, list, details);
        VBox.setVgrow(body, Priority.ALWAYS);

        notice.setWrapText(true);
        notice.getStyleClass().add("product-notice");
        notice.visibleProperty().bind(notice.textProperty().isNotEmpty());
        notice.managedProperty().bind(notice.visibleProperty());
        notice.textProperty().addListener((o, a, text) -> {
            boolean info = text.startsWith("Loading") || text.startsWith("Saving") || text.startsWith("Saved");
            notice.getStyleClass().removeAll("notice-info", "notice-error");
            notice.getStyleClass().add(info ? "notice-info" : "notice-error");
        });

        getChildren().addAll(header, stats, toolbar, notice, body);
        search.textProperty().addListener((o, a, b) -> filter());
        category.setOnAction(e -> filter());
        level.setOnAction(e -> filter());
        status.setOnAction(e -> filter());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (!editing) detail(b);
        });
        detail(null);
    }

    public void onOpen() {
        if (busy || editing) return;
        Long selected = selectedId();
        setBusy(true);
        notice.setText("Loading products…");
        var products = api.products();
        var groups = api.categories();
        var stock = reports.fetchInventoryValuation();
        CompletableFuture.allOf(products, groups, stock).whenComplete((ignored, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                notice.setText("Couldn’t refresh products. " + message(error));
                return;
            }
            applyCategories(groups.join());
            quantities.clear();
            stock.join().forEach(item -> quantities.put(item.productId(), item.stockQuantity()));
            catalogue.clear();
            catalogue.addAll(products.join());
            notice.setText("");
            filter(selected);
        }));
    }

    // ------------------------------------------------------------------ categories

    private void applyCategories(List<Category> loaded) {
        Category previous = category.getValue();
        categories.clear();
        categories.addAll(loaded);
        categories.sort(Comparator.comparing(Category::name, String.CASE_INSENSITIVE_ORDER));
        category.getItems().setAll(allCategories);
        category.getItems().addAll(categories);
        category.setValue(categories.stream().filter(c -> previous != null && c.id() == previous.id())
                .findFirst().orElse(allCategories));
    }

    private void reloadCategories(Runnable done) {
        api.categories().whenComplete((list, error) -> Platform.runLater(() -> {
            if (error != null) { notice.setText("Couldn’t reload categories. " + message(error)); return; }
            applyCategories(list);
            done.run();
        }));
    }

    private void manageCategories() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Manage categories");
        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(470);

        ListView<Category> list = new ListView<>();
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setGraphic(null); return; }
                VBox text = new VBox(1, styled(c.name(), "product-name"));
                if (c.description() != null && !c.description().isBlank())
                    text.getChildren().add(styled(c.description(), "product-muted"));
                HBox row = new HBox(12, ProductIcons.forProduct(c.name(), c.name(), 38), text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        list.setPlaceholder(new Label("No categories yet. Add the first one below."));
        list.getItems().setAll(categories);
        list.setPrefHeight(250);

        TextField name = new TextField();
        name.setPromptText("e.g. Monitors");
        TextField desc = new TextField();
        desc.setPromptText("Optional");
        Label error = styled("", "product-error");
        error.setWrapText(true);
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());

        list.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> {
            name.setText(c == null ? "" : c.name());
            desc.setText(c == null || c.description() == null ? "" : c.description());
        });

        Button addCategory = new Button("Add new");
        addCategory.getStyleClass().add("product-primary");
        Button save = new Button("Save changes");
        Button delete = new Button("Delete");
        delete.getStyleClass().add("product-danger");
        save.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        VBox content = new VBox(12,
                styled("Manage categories", "product-heading"),
                styled("Select a category to rename or delete it, or add a new one.", "product-muted"),
                list, styled("DETAILS", "product-subhead"),
                input("Name", name), input("Description", desc), error,
                line(addCategory, save, spacer(), delete));
        pane.setContent(content);

        Runnable refreshList = () -> {
            list.getItems().setAll(categories);
            list.getSelectionModel().clearSelection();
            name.clear();
            desc.clear();
        };
        Consumer<CompletableFuture<?>> run = request -> {
            content.setDisable(true);
            error.setText("");
            request.whenComplete((r, e) -> Platform.runLater(() -> {
                if (e != null) { content.setDisable(false); error.setText(message(e)); return; }
                reloadCategories(() -> { content.setDisable(false); refreshList.run(); });
            }));
        };

        addCategory.setOnAction(e -> {
            if (name.getText().isBlank()) { error.setText("Enter a category name."); return; }
            run.accept(api.createCategory(name.getText().strip(), blankToNull(desc.getText())));
        });
        save.setOnAction(e -> {
            if (name.getText().isBlank()) { error.setText("Enter a category name."); return; }
            run.accept(api.updateCategory(list.getSelectionModel().getSelectedItem().id(),
                    name.getText().strip(), blankToNull(desc.getText())));
        });
        delete.setOnAction(e -> {
            Category selected = list.getSelectionModel().getSelectedItem();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete the category “" + selected.name() + "”?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(pane.getScene().getWindow());
            styleDialog(confirm.getDialogPane());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                run.accept(api.deleteCategory(selected.id()));
        });

        dialog.showAndWait();
        onOpen(); // products embed the category name, so refresh after renames and deletes
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static void styleDialog(DialogPane pane) {
        pane.getStylesheets().add(ProductsView.class.getResource("products.css").toExternalForm());
        pane.getStyleClass().add("products-page");
    }

    // ------------------------------------------------------------------ table

    private void makeTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No matching products"));
        TableColumn<Product, Product> identity = new TableColumn<>("Product");
        identity.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        identity.setSortable(false);
        identity.setPrefWidth(290);
        identity.getStyleClass().add("align-left");
        identity.setCellFactory(col -> new TableCell<>() {
            { setContentDisplay(ContentDisplay.GRAPHIC_ONLY); getStyleClass().add("align-left"); }
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); return; }
                VBox text = new VBox(2, styled(p.name(), "product-name"),
                        styled(p.sku() + "  ·  " + p.category().name(), "product-muted"));
                text.setAlignment(Pos.CENTER_LEFT);
                HBox row = new HBox(14, ProductIcons.forProduct(p.name(), p.category().name(), 48), text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        table.getColumns().add(identity);
        table.getColumns().add(column("On hand", 80, p -> quantity(p), Pos.CENTER));
        TableColumn<Product, String> stock = column("Stock level", 124, this::stockLevel, Pos.CENTER_LEFT);
        stock.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("align-left"); }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty || value == null ? null : badge(value));
            }
        });
        table.getColumns().add(stock);
        table.getColumns().add(column("Price (MAD)", 120, p -> money.format(p.sellingPriceMad()), Pos.CENTER_RIGHT));
        table.setFixedCellSize(72);
    }

    private TableColumn<Product,String> column(String title, int width, Function<Product,String> text, Pos align) {
        TableColumn<Product,String> col = new TableColumn<>(title);
        String alignClass = align == Pos.CENTER_RIGHT ? "align-right" : align == Pos.CENTER ? "align-center" : "align-left";
        col.getStyleClass().add(alignClass);
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(text.apply(v.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            { getStyleClass().add(alignClass); }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
            }
        });
        return col;
    }

    private String quantity(Product p) {
        Long qty = quantities.get(p.id());
        return qty == null ? "—" : qty.toString();
    }

    private String stockLevel(Product p) {
        Long qty = quantities.get(p.id());
        if (qty == null) return "Unavailable";
        if (qty <= 0) return "Out of stock";
        return qty <= p.lowStockThreshold() ? "Low stock" : "In stock";
    }

    private Long selectedId() {
        Product selected = table.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.id();
    }

    private void updateStats() {
        List<Product> active = catalogue.stream().filter(p -> "ACTIVE".equals(p.status())).toList();
        statTotal.setText(String.valueOf(active.size()));
        statIn.setText(String.valueOf(active.stream().filter(p -> "In stock".equals(stockLevel(p))).count()));
        statLow.setText(String.valueOf(active.stream().filter(p -> "Low stock".equals(stockLevel(p))).count()));
        statOut.setText(String.valueOf(active.stream().filter(p -> "Out of stock".equals(stockLevel(p))).count()));
    }

    private void filter() { filter(selectedId()); }

    private void filter(Long preferred) {
        updateStats();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        Category group = category.getValue();
        var rows = catalogue.stream()
                .filter(p -> (p.name() + " " + p.sku()).toLowerCase(Locale.ROOT).contains(query))
                .filter(p -> group == null || group == allCategories || p.category().id() == group.id())
                .filter(p -> "All stock levels".equals(level.getValue()) || stockLevel(p).equals(level.getValue()))
                .filter(p -> "All statuses".equals(status.getValue()) || p.status().equalsIgnoreCase(status.getValue()))
                .sorted(Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Product::sku))
                .toList();
        table.getItems().setAll(rows);
        count.setText(rows.size() + (rows.size() == 1 ? " product" : " products"));
        Product p = rows.stream().filter(item -> Objects.equals(item.id(), preferred)).findFirst()
                .orElse(rows.isEmpty() ? null : rows.get(0));
        table.getSelectionModel().select(p);
        if (!editing) detail(p);
    }

    // ------------------------------------------------------------------ inspector

    private void detail(Product p) {
        inspector.getChildren().clear();
        if (p == null) {
            VBox empty = new VBox(8, ProductIcons.stat("total", 64),
                    styled("Select a product", "product-heading"),
                    styled("Pick a product from the list to see its details.", "product-muted"));
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("empty-state");
            inspector.getChildren().add(empty);
            return;
        }
        boolean active = "ACTIVE".equals(p.status());
        Label name = styled(p.name(), "product-hero-name");
        name.setWrapText(true);
        HBox badges = line(badge(active ? "Active" : "Inactive"), badge(stockLevel(p)));
        HBox hero = new HBox(16, ProductIcons.forProduct(p.name(), p.category().name(), 76),
                new VBox(5, name, styled(p.sku(), "product-muted"), badges));
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox price = new VBox(2, styled("Selling price", "product-muted"),
                styled(money.format(p.sellingPriceMad()) + " MAD", "product-price"));
        VBox cost = new VBox(2, styled("Average cost", "product-muted"),
                styled(p.averageCostMad() == null ? "—" : money.format(p.averageCostMad()) + " MAD", "product-value-lg"));
        cost.setAlignment(Pos.CENTER_RIGHT);
        HBox priceBox = line(price, spacer(), cost);
        priceBox.getStyleClass().add("price-box");

        HBox tiles = new HBox(12, miniStat("On hand", quantity(p)),
                miniStat("Low-stock at", String.valueOf(p.lowStockThreshold())));

        Button edit = new Button("Edit product");
        edit.getStyleClass().add("product-primary");
        edit.setOnAction(e -> form(p));
        String action = active ? "Deactivate" : "Reactivate";
        Button lifecycle = new Button(action);
        if (active) lifecycle.getStyleClass().add("product-danger");
        lifecycle.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, action + " " + p.sku() + "?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(getScene().getWindow());
            styleDialog(confirm.getDialogPane());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                persist(api.changeStatus(p), false);
        });

        inspector.getChildren().addAll(hero, priceBox, tiles,
                styled("DETAILS", "product-subhead"),
                field("Category", p.category().name()),
                field("Warranty", p.warrantyMonths() + " months"),
                field("Serial tracking", p.tracksSerials() ? "On" : "Off"));
        if (p.description() != null && !p.description().isBlank()) {
            Label description = new Label(p.description());
            description.setWrapText(true);
            description.getStyleClass().add("product-description");
            inspector.getChildren().addAll(styled("DESCRIPTION", "product-subhead"), description);
        }
        HBox actions = line(edit, lifecycle);
        actions.setSpacing(10);
        inspector.getChildren().addAll(new Separator(), actions);
    }

    private void form(Product p) {
        boolean creating = p == null;
        editing = true;
        controls();
        notice.setText("");
        TextField sku = new TextField(creating ? "" : p.sku());
        TextField name = new TextField(creating ? "" : p.name());
        TextField price = new TextField(creating ? "" : p.sellingPriceMad().toPlainString());
        TextArea description = new TextArea(creating ? "" : p.description());
        description.setPrefRowCount(3);
        description.setWrapText(true);
        ComboBox<Category> group = new ComboBox<>();
        group.getItems().setAll(categories);
        group.setPromptText("Choose category");
        group.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(group, Priority.ALWAYS);
        CheckBox serials = new CheckBox("Track individual serial numbers");
        TextField warranty = new TextField("0");
        TextField threshold = new TextField("0");

        Button newCategory = new Button("+ New category");
        newCategory.setOnAction(e -> {
            TextInputDialog ask = new TextInputDialog();
            ask.initOwner(getScene().getWindow());
            styleDialog(ask.getDialogPane());
            ask.setHeaderText("New category");
            ask.setContentText("Name:");
            ask.showAndWait().map(String::strip).filter(s -> !s.isEmpty()).ifPresent(n -> {
                setBusy(true);
                api.createCategory(n, null).whenComplete((saved, error) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (error != null) { notice.setText("Couldn’t create category. " + message(error)); return; }
                    notice.setText("");
                    List<Category> all = new ArrayList<>(categories);
                    all.add(saved);
                    applyCategories(all);
                    group.getItems().setAll(categories);
                    group.setValue(categories.stream().filter(c -> c.id() == saved.id()).findFirst().orElse(null));
                }));
            });
        });
        VBox categoryField = new VBox(4, new Label("Category"), line(group, newCategory));

        inspector.getChildren().setAll(styled(creating ? "New product" : "Edit product", "product-heading"),
                styled(creating ? "Fill in the details below to add it to the catalogue."
                        : "Update the name, SKU, price or description.", "product-muted"),
                input("Product name", name), input("SKU", sku), input("Selling price (MAD)", price),
                input("Description", description));
        if (creating) inspector.getChildren().addAll(categoryField, serials,
                input("Warranty (months)", warranty), input("Low-stock threshold", threshold));
        Button save = new Button("Save product");
        save.getStyleClass().add("product-primary");
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> {
            editing = false;
            controls();
            notice.setText("");
            detail(table.getSelectionModel().getSelectedItem());
        });
        save.setOnAction(e -> {
            try {
                String code = sku.getText().strip();
                String title = name.getText().strip();
                if (code.isEmpty() || title.isEmpty()) throw new IllegalArgumentException("Enter a SKU and product name.");
                if (code.length() > 80 || title.length() > 200)
                    throw new IllegalArgumentException("Use at most 80 characters for SKU and 200 for name.");
                BigDecimal amount = new BigDecimal(price.getText().strip());
                BigDecimal normalized = amount.stripTrailingZeros();
                if (amount.signum() < 0 || normalized.scale() > 4 || normalized.precision() - normalized.scale() > 15)
                    throw new IllegalArgumentException("Enter a nonnegative price with up to 15 integer and 4 decimal digits.");
                String desc = description.getText();
                if (creating) {
                    if (group.getValue() == null) throw new IllegalArgumentException("Choose a category.");
                    int months = Integer.parseInt(warranty.getText().strip());
                    int minimum = Integer.parseInt(threshold.getText().strip());
                    if (months < 0 || minimum < 0) throw new IllegalArgumentException("Warranty and threshold must be zero or greater.");
                    persist(api.create(new ProductApiClient.Create(group.getValue().id(), code, title,
                            serials.isSelected(), desc, amount, months, minimum)), true);
                } else {
                    persist(api.edit(p.id(), new ProductApiClient.Edit(code, title, desc, amount)), false);
                }
            } catch (NumberFormatException ex) {
                notice.setText("Enter a numeric price and whole numbers for warranty and threshold.");
            } catch (IllegalArgumentException ex) {
                notice.setText(ex.getMessage());
            }
        });
        HBox buttons = line(save, cancel);
        buttons.setSpacing(10);
        inspector.getChildren().add(buttons);
        name.requestFocus();
    }

    private void persist(CompletableFuture<Product> request, boolean creating) {
        setBusy(true);
        notice.setText("Saving…");
        request.whenComplete((saved, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                notice.setText("Couldn’t save product. " + message(error));
                return;
            }
            editing = false;
            controls();
            catalogue.removeIf(p -> p.id() == saved.id());
            catalogue.add(saved);
            if (creating) quantities.put(saved.id(), 0L);
            filter(saved.id());
            notice.setText(table.getItems().contains(saved) ? "Saved " + saved.sku() + "."
                    : "Saved " + saved.sku() + ". It falls outside the current filters.");
        }));
    }

    private void setBusy(boolean value) {
        busy = value;
        inspector.setDisable(value);
        controls();
    }

    private void controls() {
        boolean locked = busy || editing;
        toolbar.setDisable(locked);
        table.setDisable(locked);
        add.setDisable(locked);
        refresh.setDisable(locked);
        manageCategories.setDisable(locked);
    }

    // ------------------------------------------------------------------ helpers

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static ComboBox<String> choices(String... values) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().setAll(values);
        box.setValue(values[0]);
        return box;
    }

    private static HBox statCard(String title, String kind, Label value) {
        value.getStyleClass().add("stat-value");
        HBox card = new HBox(14, ProductIcons.stat(kind, 48), new VBox(0, value, styled(title, "product-muted")));
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(1);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private static VBox miniStat(String title, String value) {
        VBox tile = new VBox(2, styled(value, "mini-value"), styled(title, "product-muted"));
        tile.getStyleClass().add("mini-stat");
        tile.setPrefWidth(1);
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }

    private static HBox field(String title, String value) {
        Label right = new Label(value);
        right.setWrapText(true);
        right.getStyleClass().add("product-value");
        HBox row = line(styled(title, "product-muted"), spacer(), right);
        row.getStyleClass().add("detail-row");
        return row;
    }

    private static VBox input(String title, Control control) {
        Label label = new Label(title);
        label.getStyleClass().add("field-label");
        label.setLabelFor(control);
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, control);
    }

    private static Label badge(String text) {
        Label badge = styled(text, "product-badge");
        badge.getStyleClass().add("badge-" + text.toLowerCase(Locale.ROOT).replace(' ', '-'));
        Region dot = new Region();
        dot.getStyleClass().add("badge-dot");
        badge.setGraphic(dot);
        badge.setGraphicTextGap(6);
        return badge;
    }

    private static Label styled(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static HBox line(Node... children) {
        HBox box = new HBox(8, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region spacer() {
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        return space;
    }
}