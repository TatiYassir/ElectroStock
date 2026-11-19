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
import java.util.function.Function;

import com.electrostock.frontend.api.InventoryReportClient;
import com.electrostock.frontend.api.ProductApiClient;
import com.electrostock.frontend.api.ProductApiClient.Category;
import com.electrostock.frontend.api.ProductApiClient.Product;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ProductsView extends VBox {
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
    private final VBox inspector = new VBox(12);
    private final Button add = new Button("+  New product");
    private final Button refresh = new Button("Refresh");
    private final HBox toolbar = new HBox(10, search, category, level, status);
    private boolean busy;
    private boolean editing;

    public ProductsView() {
        getStyleClass().add("products-page");
        getStylesheets().add(ProductsView.class.getResource("products.css").toExternalForm());
        add.getStyleClass().add("product-primary");
        add.setOnAction(e -> form(null));
        refresh.setOnAction(e -> onOpen());
        search.setPromptText("Search by name or SKU");
        HBox.setHgrow(search, Priority.ALWAYS);
        category.getItems().add(allCategories);
        category.setValue(allCategories);
        makeTable();

        Label title = styled("Products", "product-title");
        HBox header = line(title, spacer(), refresh, add);
        VBox list = new VBox(12, line(styled("Product catalogue", "product-heading"), spacer(), count), table);
        list.getStyleClass().add("product-card");
        list.setMinWidth(0);
        VBox.setVgrow(table, Priority.ALWAYS);
        ScrollPane details = new ScrollPane(inspector);
        details.setFitToWidth(true);
        details.getStyleClass().add("product-detail-scroll");
        details.setMinWidth(0);
        inspector.getStyleClass().add("product-card");
        GridPane body = new GridPane();
        body.setHgap(12);
        for (double percent : new double[]{63, 37}) {
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
        notice.visibleProperty().bind(notice.textProperty().isNotEmpty());
        notice.managedProperty().bind(notice.visibleProperty());
        getChildren().addAll(header, toolbar, notice, body);
        search.textProperty().addListener((o,a,b) -> filter());
        category.setOnAction(e -> filter());
        level.setOnAction(e -> filter());
        status.setOnAction(e -> filter());
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> {
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
            Category previous = category.getValue();
            categories.clear();
            categories.addAll(groups.join());
            categories.sort(Comparator.comparing(Category::name, String.CASE_INSENSITIVE_ORDER));
            category.getItems().setAll(allCategories);
            category.getItems().addAll(categories);
            category.setValue(categories.stream().filter(c -> previous != null && c.id() == previous.id())
                    .findFirst().orElse(allCategories));
            quantities.clear();
            stock.join().forEach(item -> quantities.put(item.productId(), item.stockQuantity()));
            catalogue.clear();
            catalogue.addAll(products.join());
            notice.setText("");
            filter(selected);
        }));
    }

    private void makeTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No matching products"));
        TableColumn<Product, Product> identity = new TableColumn<>("Product");
        identity.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        identity.setSortable(false);
        identity.setPrefWidth(260);
        identity.getStyleClass().add("align-left");
        identity.setCellFactory(col -> new TableCell<>() {
            { setContentDisplay(ContentDisplay.GRAPHIC_ONLY); getStyleClass().add("align-left"); }
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); return; }
                VBox text = new VBox(2, styled(p.name(), "product-name"), styled(p.sku(), "product-muted"));
                text.setAlignment(Pos.CENTER_LEFT);
                HBox row = new HBox(12, ProductIcons.forProduct(p.name(), p.category().name(), 46), text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        table.getColumns().add(identity);
        table.getColumns().add(column("On hand", 80, p -> quantity(p), Pos.CENTER));
        TableColumn<Product, String> stock = column("Stock level", 116, this::stockLevel, Pos.CENTER_LEFT);
        stock.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("align-left"); }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty || value == null ? null : badge(value));
            }
        });
        table.getColumns().add(stock);
        table.getColumns().add(column("Price (MAD)", 120, p -> money.format(p.sellingPriceMad()), Pos.CENTER_RIGHT));
        table.setFixedCellSize(68);
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

    private void filter() { filter(selectedId()); }

    private void filter(Long preferred) {
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

    private void detail(Product p) {
        inspector.getChildren().clear();
        if (p == null) {
            inspector.getChildren().add(new Label("Select a product to see its details."));
            return;
        }
        Label name = styled(p.name(), "product-heading");
        name.setWrapText(true);
        Button edit = new Button("Edit product");
        edit.setOnAction(e -> form(p));
        Label state = badge("ACTIVE".equals(p.status()) ? "Active" : "Inactive");
        String action = "ACTIVE".equals(p.status()) ? "Deactivate" : "Reactivate";
        Button lifecycle = new Button(action);
        lifecycle.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, action + " " + p.sku() + "?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(getScene().getWindow());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                persist(api.changeStatus(p), false);
        });
        inspector.getChildren().addAll(line(ProductIcons.forProduct(p.name(), p.category().name(), 64), state, spacer(), edit),
                name, styled(p.sku(), "product-muted"), new Separator(),
                styled("Selling price", "product-muted"), styled(money.format(p.sellingPriceMad()) + " MAD", "product-price"),
                styled("Inventory", "product-subhead"), field("On hand", quantity(p)),
                field("Low-stock threshold", String.valueOf(p.lowStockThreshold())),
                field("Serial tracking", p.tracksSerials() ? "On" : "Off"),
                styled("Product details", "product-subhead"), field("Category", p.category().name()),
                field("Warranty", p.warrantyMonths() + " months"));
        if (p.description() != null && !p.description().isBlank()) {
            Label description = new Label(p.description());
            description.setWrapText(true);
            inspector.getChildren().add(description);
        }
        inspector.getChildren().addAll(new Separator(), lifecycle);
    }

    private void form(Product p) {
        boolean creating = p == null;
        if (creating && categories.isEmpty()) {
            notice.setText("Create a category before adding a product, then refresh.");
            return;
        }
        editing = true;
        controls();
        notice.setText("");
        TextField sku = new TextField(creating ? "" : p.sku());
        TextField name = new TextField(creating ? "" : p.name());
        TextField price = new TextField(creating ? "" : p.sellingPriceMad().toPlainString());
        TextArea description = new TextArea(creating ? "" : p.description());
        description.setPrefRowCount(2);
        description.setWrapText(true);
        ComboBox<Category> group = new ComboBox<>();
        group.getItems().setAll(categories);
        group.setPromptText("Choose category");
        CheckBox serials = new CheckBox("Track individual serial numbers");
        TextField warranty = new TextField("0");
        TextField threshold = new TextField("0");
        inspector.getChildren().setAll(styled(creating ? "New product" : "Edit product", "product-heading"),
                input("Product name", name), input("SKU", sku), input("Selling price (MAD)", price),
                input("Description", description));
        if (creating) inspector.getChildren().addAll(input("Category", group), serials,
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
        inspector.getChildren().add(line(save, cancel));
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
        toolbar.setDisable(busy || editing);
        table.setDisable(busy || editing);
        add.setDisable(busy || editing);
        refresh.setDisable(busy || editing);
    }

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

    private static HBox field(String title, String value) {
        Label right = new Label(value);
        right.setWrapText(true);
        right.getStyleClass().add("product-value");
        return line(styled(title, "product-muted"), spacer(), right);
    }

    private static VBox input(String title, Control control) {
        Label label = new Label(title);
        label.setLabelFor(control);
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, label, control);
    }

    private static Label badge(String text) {
        Label badge = styled(text, "product-badge");
        badge.getStyleClass().add("badge-" + text.toLowerCase(Locale.ROOT).replace(' ', '-'));
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