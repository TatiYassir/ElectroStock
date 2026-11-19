package com.electrostock.frontend.view;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.electrostock.frontend.api.SupplierApiClient;
import com.electrostock.frontend.api.SupplierApiClient.SupplierInput;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Supplier directory. All edits go through the existing Spring Boot API. */
public final class SuppliersView extends VBox {
    private enum Filter {
        ALL("All suppliers"), ACTIVE("Active"), INACTIVE("Inactive");

        private final String label;
        Filter(String label) { this.label = label; }
        @Override public String toString() { return label; }

        boolean matches(SupplierApiClient.Supplier supplier) {
            return this == ALL || name().equals(supplier.status());
        }
    }

    private final SupplierApiClient api = new SupplierApiClient();
    private final SimpleBooleanProperty writing = new SimpleBooleanProperty();
    private List<SupplierApiClient.Supplier> lastLoaded = List.of();
    private long screen;
    private String searchText = "";
    private Filter currentFilter = Filter.ALL;

    public SuppliersView() {
        getStyleClass().add("suppliers-page");
        setMinWidth(0);
        var css = SuppliersView.class.getResource("suppliers.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
    }

    public void onOpen() {
        showDirectory(null, "");
    }

    private void showDirectory(Long selectedId, String notice) {
        long page = ++screen;
        Label feedback = feedback();
        showFeedback(feedback, notice, false);
        var rows = FXCollections.observableArrayList(lastLoaded);
        var shown = new FilteredList<>(rows);
        var sorted = new SortedList<>(shown);
        TableView<SupplierApiClient.Supplier> table = new TableView<>();
        table.getStyleClass().add("supplier-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(150);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        Label empty = new Label("Loading suppliers…");
        table.setPlaceholder(empty);
        addColumn(table, "Supplier", 235, SupplierApiClient.Supplier::name);
        addColumn(table, "Phone", 145, SupplierApiClient.Supplier::phone);
        addColumn(table, "Email", 225, SupplierApiClient.Supplier::email);
        addStatusColumn(table);

        Label total = metricValue(), active = metricValue(), inactive = metricValue();
        HBox metrics = bar(metric("Total suppliers", total), metric("Active", active), metric("Inactive", inactive));
        Label count = muted("Loading…");
        TextField search = new TextField(searchText);
        search.setPromptText("Search name, phone or email");
        search.setAccessibleText("Search suppliers by name, phone or email");
        HBox.setHgrow(search, Priority.ALWAYS);
        ComboBox<Filter> status = new ComboBox<>(FXCollections.observableArrayList(Filter.values()));
        status.setAccessibleText("Filter by supplier status");
        status.setValue(currentFilter);
        Runnable applyFilter = () -> {
            searchText = search.getText();
            currentFilter = status.getValue() == null ? Filter.ALL : status.getValue();
            shown.setPredicate(supplier -> currentFilter.matches(supplier) && matches(supplier, searchText));
            count.setText(shown.size() + " of " + rows.size() + " suppliers");
        };
        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());
        status.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());

        var selection = table.getSelectionModel().selectedItemProperty();
        Button edit = button("Edit supplier", "supplier-secondary", () -> {
            var supplier = selection.get();
            if (supplier != null) openEdit(supplier.id());
        });
        edit.disableProperty().bind(writing.or(selection.isNull()));
        Button changeStatus = button("Deactivate", "supplier-secondary", () -> {
            var supplier = selection.get();
            if (supplier != null) changeStatus(supplier, feedback);
        });
        changeStatus.disableProperty().bind(writing.or(selection.isNull()));

        Label address = muted("");
        address.getStyleClass().add("supplier-address");
        address.setWrapText(true);
        address.setMaxWidth(Double.MAX_VALUE);
        address.visibleProperty().bind(selection.isNotNull());
        address.managedProperty().bind(address.visibleProperty());
        selection.addListener((obs, previous, supplier) -> {
            boolean deactivate = supplier != null && supplier.active();
            changeStatus.setText(supplier != null && !deactivate ? "Reactivate" : "Deactivate");
            changeStatus.getStyleClass().removeAll("supplier-danger", "supplier-secondary");
            changeStatus.getStyleClass().add(deactivate ? "supplier-danger" : "supplier-secondary");
            address.setText(supplier == null ? "" : "Address  ·  " + display(supplier.address()));
        });
        table.setRowFactory(ignored -> {
            TableRow<SupplierApiClient.Supplier> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    openEdit(row.getItem().id());
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                edit.fire();
                event.consume();
            }
        });

        Button refresh = button("Refresh", "supplier-secondary", () -> {
            var picked = selection.get();
            showDirectory(picked == null ? null : picked.id(), "");
        });
        Button create = button("+ New supplier", "supplier-primary", () -> showForm(null));
        refresh.disableProperty().bind(writing);
        create.disableProperty().bind(writing);

        VBox directory = card(bar(heading("Supplier directory"), spacer(), count),
                bar(search, status, edit, changeStatus), table, address);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(directory, Priority.ALWAYS);
        getChildren().setAll(pageHeading("Suppliers", "Manage contacts and supplier availability for purchases.",
                refresh, create), feedback, metrics, directory);

        if (!lastLoaded.isEmpty()) updateCounts(lastLoaded, total, active, inactive);
        applyFilter.run();
        api.list().whenComplete((suppliers, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                showFeedback(feedback, (notice.isEmpty() ? "" : notice + " ") + errorMessage(error)
                        + (lastLoaded.isEmpty() ? "" : " Showing the last loaded list."), true);
                empty.setText(lastLoaded.isEmpty() ? "Suppliers could not be loaded." : "No suppliers match your filters.");
                if (lastLoaded.isEmpty()) count.setText("Not loaded");
                return;
            }
            lastLoaded = suppliers.stream()
                    .sorted(Comparator.comparing(SupplierApiClient.Supplier::name, String.CASE_INSENSITIVE_ORDER)
                            .thenComparingLong(SupplierApiClient.Supplier::id)).toList();
            rows.setAll(lastLoaded);
            updateCounts(lastLoaded, total, active, inactive);
            applyFilter.run();
            empty.setText(lastLoaded.isEmpty() ? "No suppliers yet. Choose New supplier to add one."
                    : "No suppliers match your filters.");
            if (selectedId != null) {
                sorted.stream().filter(supplier -> supplier.id() == selectedId).findFirst()
                        .ifPresent(supplier -> {
                            table.getSelectionModel().select(supplier);
                            table.scrollTo(supplier);
                        });
            }
        }));
    }

    private void openEdit(long id) {
        if (writing.get()) return;
        long page = ++screen;
        Label feedback = feedback();
        Button back = button("Back to suppliers", "supplier-secondary", this::onOpen);
        showFeedback(feedback, "Loading supplier…", false);
        getChildren().setAll(pageHeading("Edit supplier", "Update a saved supplier's contact details.", back), feedback);
        api.get(id).whenComplete((supplier, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error == null) showForm(supplier);
            else {
                showFeedback(feedback, errorMessage(error), true);
                getChildren().add(button("Retry", "supplier-secondary", () -> openEdit(id)));
            }
        }));
    }

    private void showForm(SupplierApiClient.Supplier supplier) {
        ++screen;
        boolean create = supplier == null;
        Label feedback = feedback();
        TextField name = new TextField(create ? "" : value(supplier.name()));
        name.setPromptText("Company or supplier name");
        TextField phone = new TextField(create ? "" : value(supplier.phone()));
        phone.setPromptText("e.g. +212 6 00 00 00 00");
        TextField email = new TextField(create ? "" : value(supplier.email()));
        email.setPromptText("e.g. orders@example.com");
        TextArea address = new TextArea(create ? "" : value(supplier.address()));
        address.setPromptText("Street, city or notes about the supplier's location");
        address.setWrapText(true);
        address.setPrefRowCount(4);
        for (Control input : List.of(name, phone, email, address)) {
            input.disableProperty().bind(writing);
            input.setMaxWidth(Double.MAX_VALUE);
        }

        Label validation = muted("");
        validation.getStyleClass().add("supplier-validation");
        validation.setWrapText(true);
        validation.visibleProperty().bind(validation.textProperty().isNotEmpty());
        validation.managedProperty().bind(validation.visibleProperty());
        var invalid = new SimpleBooleanProperty(true);
        Supplier<SupplierInput> input = () -> new SupplierInput(name.getText().strip(),
                optional(phone.getText()), optional(email.getText()), optional(address.getText()));
        Runnable validate = () -> {
            String issue = inputProblem(input.get());
            invalid.set(issue != null);
            validation.setText(issue == null ? "" : issue);
        };
        for (var field : List.of(name, phone, email, address)) {
            field.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        }
        validate.run();

        Button save = button(create ? "Create supplier" : "Save changes", "supplier-primary", () -> {
            SupplierInput data = input.get();
            write("Saving supplier…", feedback, () -> create ? api.create(data) : api.update(supplier.id(), data),
                    saved -> showDirectory(saved.id(), create ? "Supplier created." : "Supplier details updated."));
        });
        save.disableProperty().bind(writing.or(invalid));
        Button back = button("Back to suppliers", "supplier-secondary", () ->
                showDirectory(create ? null : supplier.id(), ""));
        back.disableProperty().bind(writing);

        GridPane contact = new GridPane();
        contact.setHgap(12);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            contact.getColumnConstraints().add(column);
        }
        contact.addRow(0, field("Phone", phone), field("Email", email));
        VBox form = card(bar(heading("Contact details"), spacer(),
                        create ? muted("New record") : badge(supplier.status())),
                field("Supplier name *", name), contact, field("Address", address),
                validation, bar(muted("* Required"), spacer(), save));
        form.setMaxWidth(725);
        getChildren().setAll(pageHeading(create ? "New supplier" : "Edit supplier",
                "Only the supplier name is required.", back), feedback, form);
    }

    private void changeStatus(SupplierApiClient.Supplier supplier, Label feedback) {
        if (writing.get()) return;
        boolean target = !supplier.active();
        String action = target ? "Reactivate" : "Deactivate";
        String description = target
                ? "The supplier will be available for new purchase drafts again."
                : "The supplier will be hidden from the active supplier list for new drafts. Existing purchases are kept.";
        ButtonType accept = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, description, ButtonType.CANCEL, accept);
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Suppliers");
        dialog.setHeaderText(action + " " + supplier.name() + "?");
        if (dialog.showAndWait().filter(accept::equals).isEmpty()) return;
        write("Updating supplier status…", feedback, () -> api.setActive(supplier.id(), target),
                saved -> showDirectory(saved.id(), target ? "Supplier reactivated." : "Supplier deactivated."));
    }

    private <T> void write(String pending, Label feedback,
            Supplier<CompletableFuture<T>> request, Consumer<T> success) {
        if (writing.get()) return;
        long page = screen;
        writing.set(true);
        showFeedback(feedback, pending, false);
        try {
            request.get().whenComplete((result, error) -> Platform.runLater(() -> {
                writing.set(false);
                if (page != screen) return;
                if (error != null) showFeedback(feedback, errorMessage(error), true);
                else success.accept(result);
            }));
        } catch (RuntimeException e) {
            writing.set(false);
            showFeedback(feedback, errorMessage(e), true);
        }
    }

    static String inputProblem(SupplierInput input) {
        if (input.name() == null || input.name().isBlank()) return "Enter the supplier name.";
        if (tooLong(input.name(), 200)) return "Supplier names can contain up to 200 characters.";
        if (tooLong(input.phone(), 30)) return "Phone numbers can contain up to 30 characters.";
        if (tooLong(input.email(), 254)) return "Email addresses can contain up to 254 characters.";
        if (input.address() != null && input.address().getBytes(StandardCharsets.UTF_8).length > 65_535) {
            return "The address is too long.";
        }
        return null;
    }

    private static boolean tooLong(String value, int limit) {
        return value != null && value.codePointCount(0, value.length()) > limit;
    }

    private static boolean matches(SupplierApiClient.Supplier supplier, String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) return true;
        boolean textMatch = List.of(value(supplier.name()), value(supplier.phone()), value(supplier.email())).stream()
                .anyMatch(field -> field.toLowerCase(Locale.ROOT).contains(term));
        String digits = term.replaceAll("\\D", "");
        return textMatch || (!digits.isEmpty() && term.matches("[+() .0-9-]+")
                && value(supplier.phone()).replaceAll("\\D", "").contains(digits));
    }

    private static void updateCounts(List<SupplierApiClient.Supplier> suppliers, Label total, Label active, Label inactive) {
        total.setText(String.valueOf(suppliers.size()));
        active.setText(String.valueOf(suppliers.stream().filter(SupplierApiClient.Supplier::active).count()));
        inactive.setText(String.valueOf(suppliers.stream().filter(row -> "INACTIVE".equals(row.status())).count()));
    }

    private static void addColumn(TableView<SupplierApiClient.Supplier> table, String title,
            double width, Function<SupplierApiClient.Supplier, String> text) {
        TableColumn<SupplierApiClient.Supplier, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(display(text.apply(cell.getValue()))));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTooltip(empty || value == null || "—".equals(value) ? null : new Tooltip(value));
            }
        });
        table.getColumns().add(column);
    }

    private static void addStatusColumn(TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, String> column = new TableColumn<>("Status");
        column.setPrefWidth(105);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setGraphic(empty ? null : badge(status));
            }
        });
        table.getColumns().add(column);
    }

    private static Label badge(String status) {
        boolean active = "ACTIVE".equals(status);
        boolean inactive = "INACTIVE".equals(status);
        Label label = new Label(active ? "Active" : inactive ? "Inactive" : "Unknown");
        label.getStyleClass().addAll("supplier-status", active ? "supplier-status-active" : "supplier-status-inactive");
        return label;
    }

    private static String optional(String text) {
        String clean = value(text).strip();
        return clean.isEmpty() ? null : clean;
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static String display(String text) { return value(text).isBlank() ? "—" : text; }

    private static String errorMessage(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        if (error instanceof ConnectException) return "Could not reach the backend. Check that Spring Boot is running.";
        if (error instanceof HttpTimeoutException) return "The backend did not respond in time. Refresh before retrying a change.";
        return error.getMessage() == null || error.getMessage().isBlank() ? "The request failed." : error.getMessage();
    }

    private static HBox pageHeading(String title, String subtitle, Node... actions) {
        Label label = new Label(title);
        label.getStyleClass().add("supplier-page-title");
        HBox row = bar(new VBox(3, label, muted(subtitle)), spacer());
        row.getChildren().addAll(actions);
        return row;
    }

    private static Label metricValue() {
        Label label = new Label("—");
        label.getStyleClass().add("supplier-metric-value");
        return label;
    }

    private static VBox metric(String text, Label value) {
        VBox card = card(muted(text), value);
        card.getStyleClass().add("supplier-metric");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("supplier-card");
        return box;
    }

    private static HBox bar(Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region spacer() {
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        return space;
    }

    private static VBox field(String title, Control control) {
        Label label = heading(title);
        label.getStyleClass().add("supplier-field-label");
        label.setLabelFor(control);
        return new VBox(5, label, control);
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("supplier-card-title");
        return label;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("supplier-muted");
        return label;
    }

    private static Button button(String title, String css, Runnable action) {
        Button button = new Button(title);
        button.getStyleClass().add(css);
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
        label.getStyleClass().setAll("label", error ? "supplier-error" : "supplier-notice");
        label.setText(text);
        label.setVisible(!text.isBlank());
    }
}
