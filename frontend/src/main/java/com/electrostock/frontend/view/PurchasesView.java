package com.electrostock.frontend.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

import com.electrostock.frontend.api.PurchaseApiClient;
import com.electrostock.frontend.api.PurchaseApiClient.Currency;
import com.electrostock.frontend.api.PurchaseApiClient.Line;
import com.electrostock.frontend.api.PurchaseApiClient.Purchase;
import com.electrostock.frontend.api.PurchaseApiClient.Summary;
import com.electrostock.frontend.api.PurchaseApiClient.Supplier;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The Purchases page, including persisted drafts and the existing purchase lifecycle. */
public class PurchasesView extends VBox {
    private enum Status {
        ALL("All statuses", ""), DRAFT("Draft", "draft"),
        CONFIRMED("Confirmed", "confirmed"),
        CANCELLED("Cancelled", "cancelled");

        private final String text;
        private final String css;
        Status(String text, String css) { this.text = text; this.css = css; }
        @Override public String toString() { return text; }
    }

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);
    private final PurchaseApiClient api = new PurchaseApiClient();
    private final PurchaseDialogs dialogs = new PurchaseDialogs();
    private final SimpleBooleanProperty writing = new SimpleBooleanProperty();
    private long screen;
    private String searchText = "";
    private Status selectedStatus = Status.ALL;
    private record DraftOptions(List<Supplier> suppliers, List<Currency> currencies) {}

    public PurchasesView() {
        getStyleClass().add("purchases-page");
        setMinWidth(0);
        applyStyles(this);
    }

    public void showList() {
        long page = ++screen;
        Label feedback = feedback();
        var data = FXCollections.<Summary>observableArrayList();
        var filtered = new FilteredList<>(data);
        var sorted = new SortedList<>(filtered);
        TableView<Summary> table = table();
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        Label empty = new Label("Loading purchases…");
        table.setPlaceholder(empty);
        addColumn(table, "Purchase #", 84, false, row -> "#" + row.id());
        addColumn(table, "Purchase date", 140, false, row -> date(row.purchaseDate()));
        addColumn(table, "Supplier", 175, false, Summary::supplierName);
        addStatusColumn(table);
        addColumn(table, "Currency", 90, false, Summary::currencyCode);
        addColumn(table, "Total", 150, true, row -> amount(row.totalCurrency()));

        Button open = button("Open purchase", "purchase-secondary", () -> {
            Summary picked = table.getSelectionModel().getSelectedItem();
            if (picked != null) loadPurchase(picked.id());
        });
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        table.setRowFactory(ignored -> {
            TableRow<Summary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    loadPurchase(row.getItem().id());
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) open.fire();
        });

        TextField search = new TextField(searchText);
        search.setPromptText("Search by purchase number or supplier");
        search.setAccessibleText("Search by purchase number or supplier");
        HBox.setHgrow(search, Priority.ALWAYS);
        ComboBox<Status> status = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
        status.setAccessibleText("Filter by purchase status");
        status.setValue(selectedStatus);
        Label count = small("Loading…");
        Runnable applyFilters = () -> {
            searchText = search.getText();
            selectedStatus = status.getValue() == null ? Status.ALL : status.getValue();
            String query = searchText.strip().replaceFirst("^#", "").strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(row ->
                    (selectedStatus == Status.ALL || selectedStatus == statusOf(row.status()))
                    && (Long.toString(row.id()).contains(query)
                        || fallback(row.supplierName()).toLowerCase(Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + data.size() + " purchases");
        };
        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        status.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());

        Button refresh = button("Refresh", "purchase-secondary", this::showList);
        Button create = button("+ New purchase", "purchase-primary", this::showNewPurchase);
        create.disableProperty().bind(writing);
        HBox filters = bar(search, status, open);
        VBox history = card(bar(heading("Purchases history"), spacer(), count), filters, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(history, Priority.ALWAYS);
        getChildren().setAll(pageHeading("Purchases", "Record incoming stock and review purchase history.", refresh, create),
                feedback, history);
        api.list().whenComplete((rows, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                count.setText("Not loaded");
                empty.setText("Purchases could not be loaded.");
                showFeedback(feedback, message(error), true);
            } else {
                data.setAll(rows);
                applyFilters.run();
                empty.setText(rows.isEmpty() ? "No purchases yet. Create a new purchase to get started."
                        : "No purchases match your filters.");
            }
        }));
    }

    public void showNewPurchase() {
        long page = ++screen;
        Label feedback = feedback();
        DatePicker purchaseDate = new DatePicker(LocalDate.now());
        purchaseDate.setEditable(false);
        purchaseDate.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Supplier> supplier = new ComboBox<>();
        supplier.setPromptText("Loading suppliers…");
        supplier.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Currency> currency = new ComboBox<>();
        currency.setPromptText("Loading currencies…");
        currency.setMaxWidth(Double.MAX_VALUE);
        Button submit = button("Create draft", "purchase-primary", () ->
                write("Creating draft…", feedback,
                        () -> api.createDraft(supplier.getValue().id(), currency.getValue().code(),
                                purchaseDate.getValue()),
                        id -> loadPurchase(id, "Draft created. You can now add products.")));
        submit.disableProperty().bind(writing.or(currency.valueProperty().isNull())
                .or(purchaseDate.valueProperty().isNull()).or(supplier.valueProperty().isNull()));
        Button back = button("Back to purchases", "purchase-secondary", this::showList);
        VBox form = card(heading("Purchase information"), small("You can reopen the draft from Purchases history."),
                field("Supplier", supplier), field("Purchase date", purchaseDate), field("Currency", currency), submit);
        form.setMaxWidth(520);
        getChildren().setAll(pageHeading("New purchase", "Choose the supplier, invoice date, and currency.", back),
                feedback, form);
        api.suppliers().thenCombine(api.currencies(), DraftOptions::new).whenComplete((options, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                supplier.setPromptText("Supplier list unavailable");
                currency.setPromptText("Currency list unavailable");
                showFeedback(feedback, message(error), true);
                form.getChildren().add(button("Retry", "purchase-secondary", this::showNewPurchase));
            } else {
                supplier.getItems().setAll(options.suppliers().stream()
                        .filter(row -> "ACTIVE".equals(row.status())).toList());
                supplier.setPromptText(supplier.getItems().isEmpty() ? "No active suppliers" : "Choose a supplier");
                currency.getItems().setAll(options.currencies());
                currency.setPromptText("Choose a currency");
                options.currencies().stream().filter(c -> "MAD".equals(c.code())).findFirst()
                        .ifPresent(currency::setValue);
                if (supplier.getItems().isEmpty()) showFeedback(feedback, "An active supplier is needed to create a purchase.", true);
                else if (options.currencies().isEmpty()) showFeedback(feedback, "No currencies are configured.", true);
            }
        }));
    }

    private void loadPurchase(long id) {
        loadPurchase(id, "");
    }

    private void loadPurchase(long id, String notice) {
        long page = ++screen;
        Button back = button("Back to purchases", "purchase-secondary", this::showList);
        Label feedback = feedback();
        showFeedback(feedback, "Loading purchase #" + id + "…", false);
        getChildren().setAll(pageHeading("Purchase #" + id, "Purchase details", back), feedback);
        api.get(id).whenComplete((purchase, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                showFeedback(feedback, (notice.isEmpty() ? "" : notice + " ") + message(error), true);
                getChildren().add(button("Retry", "purchase-secondary", () -> loadPurchase(id, notice)));
            } else {
                renderPurchase(purchase, notice);
            }
        }));
    }

    private void renderPurchase(Purchase purchase, String notice) {
        Label feedback = feedback();
        if (!notice.isEmpty()) showFeedback(feedback, notice, false);
        Status status = statusOf(purchase.status());
        Button back = button("Back to purchases", "purchase-secondary", this::showList);
        Button refresh = button("Refresh", "purchase-secondary", () -> loadPurchase(purchase.id()));
        refresh.disableProperty().bind(writing);

        VBox summary = card(bar(heading("Purchase summary"), spacer(), badge(status)));
        HBox figures = bar(metric("Supplier", fallback(purchase.supplierName())),
                metric("Purchase date", date(purchase.purchaseDate())),
                metric("Currency", purchase.currencyCode()), metric("Total", money(purchase.totalCurrency(), purchase.currencyCode())),
                metric("Total in MAD", purchase.totalMad() == null ? "—" : money(purchase.totalMad(), "MAD")));
        summary.getChildren().add(figures);
        if (purchase.cancelledAt() != null) {
            summary.getChildren().add(small("Cancelled " + DATE_TIME.format(purchase.cancelledAt())));
        } else if (purchase.confirmedAt() != null) {
            summary.getChildren().add(small("Confirmed " + DATE_TIME.format(purchase.confirmedAt())));
        }
        if (purchase.rateFromCacheSnapshot()) {
            summary.getChildren().add(small("Confirmed using a cached exchange rate."));
        }

        TableView<Line> lines = table();
        lines.getItems().setAll(purchase.items());
        lines.setPlaceholder(new Label(status == Status.DRAFT
                ? "No items yet. Use Add product to start this draft." : "No purchase items."));
        addColumn(lines, "SKU", 110, false, line -> fallback(line.sku()));
        addColumn(lines, "Product", 190, false, line -> line.productName() == null
                ? "Product #" + line.productId() : line.productName());
        addColumn(lines, "Qty", 58, true, line -> String.valueOf(line.quantity()));
        addColumn(lines, "Unit cost", 110, true, line -> amount(line.unitPriceCurrency()));
        addColumn(lines, "Line total", 110, true, line -> amount(line.lineTotalCurrency()));
        addColumn(lines, "Serial numbers", 145, false, line ->
                line.serialNumbers() == null || line.serialNumbers().isEmpty()
                        ? (line.tracksSerials() && status == Status.DRAFT ? "Enter at confirmation" : "—")
                        : String.join(", ", line.serialNumbers()));

        HBox lineHeader = bar(heading("Purchase items"), spacer());
        HBox actions = bar();
        if (status == Status.DRAFT) {
            Button add = button("Add product", "purchase-secondary", () ->
                    dialogs.addLine(getScene().getWindow(), purchase).ifPresent(line ->
                            write("Adding product…", feedback,
                                    () -> api.addLine(purchase.id(), line.productId(), line.quantity(), line.unitPrice()),
                                    ignored -> loadPurchase(purchase.id(), "Product added to the draft."))));
            Button remove = button("Remove selected", "purchase-secondary", () -> {
                Line selected = lines.getSelectionModel().getSelectedItem();
                if (selected != null) write("Removing product…", feedback,
                        () -> api.removeLine(purchase.id(), selected.id()),
                        ignored -> loadPurchase(purchase.id(), "Product removed from the draft."));
            });
            Button confirm = button("Confirm purchase", "purchase-primary", () ->
                    dialogs.confirm(getScene().getWindow(), purchase).ifPresent(serials ->
                            write("Confirming purchase…", feedback, () -> api.confirm(purchase.id(), serials),
                                    ignored -> loadPurchase(purchase.id(), "Purchase confirmed. Received stock has been added."))));
            add.disableProperty().bind(writing);
            remove.disableProperty().bind(writing.or(lines.getSelectionModel().selectedItemProperty().isNull()));
            confirm.disableProperty().bind(writing.or(new SimpleBooleanProperty(purchase.items().isEmpty())));
            lineHeader.getChildren().addAll(remove, add);
            actions.getChildren().addAll(small("Confirm when the stock has arrived and the quantities and costs are correct."),
                    spacer(), confirm);
        } else if (status == Status.CONFIRMED) {
            Button cancel = button("Cancel purchase", "purchase-danger", () -> {
                if (approve("Cancel purchase #" + purchase.id() + "?",
                        "This reverses the stock receipt if the inventory still permits it.", "Cancel purchase")) {
                    write("Cancelling purchase…", feedback, () -> api.cancel(purchase.id()),
                            ignored -> loadPurchase(purchase.id(), "Purchase cancelled. The stock receipt was reversed."));
                }
            });
            cancel.disableProperty().bind(writing);
            actions.getChildren().addAll(spacer(), cancel);
        }

        VBox items = card(lineHeader, lines);
        if (!actions.getChildren().isEmpty()) items.getChildren().add(actions);
        VBox.setVgrow(lines, Priority.ALWAYS);
        VBox.setVgrow(items, Priority.ALWAYS);
        getChildren().setAll(pageHeading("Purchase #" + purchase.id(), "Review products, totals, and purchase status.", back, refresh),
                feedback, summary, items);
    }

    private boolean approve(String title, String detail, String action) {
        ButtonType accept = new ButtonType(action, javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, detail, ButtonType.CANCEL, accept);
        alert.initOwner(getScene().getWindow());
        alert.setTitle("Purchases");
        alert.setHeaderText(title);
        return alert.showAndWait().filter(accept::equals).isPresent();
    }

    private <T> void write(String pending, Label feedback,
            java.util.function.Supplier<CompletableFuture<T>> request, Consumer<T> success) {
        if (writing.get()) return;
        long page = screen;
        writing.set(true);
        showFeedback(feedback, pending, false);
        try {
            request.get().whenComplete((value, error) -> Platform.runLater(() -> {
                writing.set(false);
                if (page != screen) return;
                if (error != null) {
                    showFeedback(feedback, message(error) + " Refresh the purchase or list before retrying if the connection was lost.", true);
                } else {
                    success.accept(value);
                }
            }));
        } catch (RuntimeException e) {
            writing.set(false);
            showFeedback(feedback, message(e), true);
        }
    }

    private static Status statusOf(String status) {
        return switch (status) {
            case "DRAFT" -> Status.DRAFT;
            case "CANCELLED" -> Status.CANCELLED;
            case "CONFIRMED" -> Status.CONFIRMED;
            default -> Status.ALL;
        };
    }

    private static Label badge(Status status) {
        Label label = new Label(status == Status.ALL ? "Unknown" : status.text);
        label.getStyleClass().addAll("purchase-status", "purchase-status-" + status.css);
        return label;
    }

    private static void addStatusColumn(TableView<Summary> table) {
        TableColumn<Summary, String> column = new TableColumn<>("Status");
        column.setPrefWidth(172);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                statusOf(cell.getValue().status()).text));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                Summary row = getTableRow() == null ? null : getTableRow().getItem();
                setGraphic(empty || row == null ? null : badge(statusOf(row.status())));
            }
        });
        table.getColumns().add(column);
    }

    private static <T> void addColumn(TableView<T> table, String title, double width,
            boolean numeric, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        // The server already orders the list by date and ID. Avoid textual sorting of currency amounts.
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setAlignment(numeric ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                setTooltip(empty || value == null ? null : new Tooltip(value));
            }
        });
        table.getColumns().add(column);
    }

    private static <T> TableView<T> table() {
        TableView<T> table = new TableView<>();
        table.getStyleClass().add("purchase-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(130);
        return table;
    }

    private static HBox pageHeading(String title, String subtitle, Node... actions) {
        Label head = new Label(title);
        head.getStyleClass().add("purchase-page-title");
        HBox row = bar(new VBox(3, head, small(subtitle)), spacer());
        row.getChildren().addAll(actions);
        return row;
    }

    private static VBox metric(String name, String value) {
        Label number = new Label(value);
        number.getStyleClass().add("purchase-metric-value");
        VBox box = new VBox(5, small(name), number);
        box.getStyleClass().add("purchase-metric");
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("purchase-card");
        return box;
    }

    static void applyStyles(Parent parent) {
        var css = PurchasesView.class.getResource("purchases.css");
        if (css != null) parent.getStylesheets().add(css.toExternalForm());
    }

    static HBox bar(Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static VBox field(String title, javafx.scene.control.Control control) {
        Label label = new Label(title);
        label.getStyleClass().add("purchase-field-label");
        label.setLabelFor(control);
        return new VBox(5, label, control);
    }

    static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("purchase-card-title");
        return label;
    }

    static Label small(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("purchase-muted");
        return label;
    }

    private static Region spacer() {
        Region node = new Region();
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }

    private static Button button(String caption, String style, Runnable action) {
        Button button = new Button(caption);
        button.getStyleClass().add(style);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Label feedback() {
        Label label = new Label();
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setVisible(false);
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    private static void showFeedback(Label label, String text, boolean error) {
        label.getStyleClass().setAll("label", error ? "purchase-error" : "purchase-notice");
        label.setText(text);
        label.setVisible(!text.isBlank());
    }

    static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        if (error instanceof HttpTimeoutException) return "The backend did not respond in time.";
        if (error instanceof ConnectException) return "Could not reach the backend. Check that Spring Boot is running.";
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? "The request failed." : detail;
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String date(LocalDate date) {
        return date == null ? "—" : DATE.format(date);
    }

    static String money(BigDecimal value, String code) {
        return value == null ? "—" : amount(value) + " " + code;
    }

    static String amount(BigDecimal value) {
        if (value == null) return "—";
        int scale = Math.max(2, Math.min(4, value.stripTrailingZeros().scale()));
        return String.format(Locale.ENGLISH, "%,." + scale + "f", value.setScale(scale, RoundingMode.HALF_UP));
    }
}