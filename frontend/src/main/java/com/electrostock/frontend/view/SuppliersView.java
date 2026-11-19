package com.electrostock.frontend.view;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.EnumMap;
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
import javafx.beans.property.ReadOnlyObjectWrapper;
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
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Supplier directory. All edits go through the existing Spring Boot API. */
public final class SuppliersView extends VBox {
    private enum Filter {
        ALL("All suppliers", "All", "all"),
        ACTIVE("Active", "Active", "active"),
        INACTIVE("Inactive", "Inactive", "inactive");

        private final String label;
        private final String chipLabel;
        private final String style;

        Filter(String label, String chipLabel, String style) {
            this.label = label;
            this.chipLabel = chipLabel;
            this.style = style;
        }

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
    private boolean exporting;

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
        table.setAccessibleText("Supplier directory");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(170);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        Label empty = new Label("Loading suppliers…");
        table.setPlaceholder(empty);

        addSupplierColumn(table);
        addContactColumn(table, "Phone", 150, SupplierApiClient.Supplier::phone, "supplier-cell-phone");
        addContactColumn(table, "Email", 215, SupplierApiClient.Supplier::email, "supplier-cell-email");
        addAddressColumn(table);
        addStatusColumn(table);

        // Interactive KPI Cards
        var metricValues = new EnumMap<Filter, Label>(Filter.class);
        var metricSubs = new EnumMap<Filter, Label>(Filter.class);
        var metricCards = new EnumMap<Filter, VBox>(Filter.class);
        var filterTabs = new EnumMap<Filter, ToggleButton>(Filter.class);

        Label count = label("Loading…", "supplier-count-pill");
        TextField search = new TextField(searchText);
        search.getStyleClass().add("supplier-search");
        search.setPromptText("Search by supplier name, phone, email, or address…");
        search.setAccessibleText("Search suppliers by name, phone, email, or address");
        search.setMinWidth(180);
        HBox.setHgrow(search, Priority.ALWAYS);

        Button clearSearch = button("Clear", "supplier-chip-btn", search::clear);
        show(clearSearch, searchText != null && !searchText.isEmpty());

        Button export = button("Export CSV", "supplier-secondary", () -> {});
        export.setDisable(true);
        export.setTooltip(new Tooltip("Export the currently visible suppliers to CSV."));

        Runnable syncFilterUi = () -> {
            ToggleButton activeTab = filterTabs.get(currentFilter);
            if (activeTab != null && !activeTab.isSelected()) {
                activeTab.setSelected(true);
            }
            metricCards.forEach((f, card) -> {
                card.getStyleClass().remove("supplier-metric-selected");
                if (f == currentFilter) {
                    card.getStyleClass().add("supplier-metric-selected");
                }
            });
        };

        Runnable applyFilter = () -> {
            searchText = search.getText() == null ? "" : search.getText();
            show(clearSearch, !searchText.isEmpty());
            shown.setPredicate(supplier -> currentFilter.matches(supplier) && matches(supplier, searchText));
            count.setText(shown.size() + " of " + rows.size() + (rows.size() == 1 ? " supplier" : " suppliers"));
            export.setDisable(exporting || writing.get() || shown.isEmpty());
            syncFilterUi.run();
        };

        Consumer<Filter> selectFilter = chosen -> {
            currentFilter = chosen == null ? Filter.ALL : chosen;
            applyFilter.run();
        };

        VBox totalCard = metricCard("TOTAL DIRECTORY", Filter.ALL, metricValues, metricSubs, metricCards, selectFilter);
        VBox activeCard = metricCard("ACTIVE VENDORS", Filter.ACTIVE, metricValues, metricSubs, metricCards, selectFilter);
        VBox inactiveCard = metricCard("INACTIVE / PAUSED", Filter.INACTIVE, metricValues, metricSubs, metricCards, selectFilter);
        HBox metrics = new HBox(12, totalCard, activeCard, inactiveCard);

        // Segmented Filter Bar
        HBox statusSwitch = new HBox(2);
        statusSwitch.getStyleClass().add("supplier-seg-group");
        statusSwitch.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup statusGroup = new ToggleGroup();
        for (Filter f : Filter.values()) {
            ToggleButton btn = new ToggleButton(f.chipLabel);
            btn.setToggleGroup(statusGroup);
            btn.getStyleClass().addAll("supplier-seg-btn", "supplier-seg-" + f.style);
            if (f == currentFilter) btn.setSelected(true);
            btn.setOnAction(e -> selectFilter.accept(f));
            filterTabs.put(f, btn);
            statusSwitch.getChildren().add(btn);
        }
        statusGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());

        export.setOnAction(e -> exportCsv(List.copyOf(table.getItems()), feedback, export, () -> {
            export.setDisable(exporting || writing.get() || shown.isEmpty());
        }));

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

        // Selected Supplier Inspector Strip
        StackPane inspectorAvatar = avatarBadge("—", true);
        Label inspectorName = label("", "supplier-inspector-name");
        Label inspectorStatus = badge("ACTIVE");
        Label inspectorContact = muted("");
        Label inspectorAddress = muted("");
        inspectorAddress.setWrapText(true);

        VBox inspectorText = new VBox(3,
                bar(inspectorName, inspectorStatus),
                inspectorContact,
                inspectorAddress);
        HBox.setHgrow(inspectorText, Priority.ALWAYS);

        HBox inspector = new HBox(14, inspectorAvatar, inspectorText);
        inspector.setAlignment(Pos.CENTER_LEFT);
        inspector.getStyleClass().add("supplier-inspector");
        inspector.visibleProperty().bind(selection.isNotNull());
        inspector.managedProperty().bind(inspector.visibleProperty());

        selection.addListener((obs, previous, supplier) -> {
            boolean deactivate = supplier != null && supplier.active();
            changeStatus.setText(supplier != null && !deactivate ? "Reactivate" : "Deactivate");
            changeStatus.getStyleClass().removeAll("supplier-danger", "supplier-secondary");
            changeStatus.getStyleClass().add(deactivate ? "supplier-danger" : "supplier-secondary");

            if (supplier != null) {
                updateAvatar(inspectorAvatar, supplier.name(), supplier.active());
                inspectorName.setText(supplier.name());
                updateStatusBadge(inspectorStatus, supplier.status());
                String phoneStr = display(supplier.phone());
                String emailStr = display(supplier.email());
                inspectorContact.setText("Phone: " + phoneStr + "   ·   Email: " + emailStr);
                String addrStr = value(supplier.address()).strip();
                inspectorAddress.setText(addrStr.isEmpty()
                        ? "Address: No physical address recorded"
                        : "Address: " + addrStr.replaceAll("\\s*\\R+\\s*", ", "));
            }
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

        VBox directory = card(
                bar(heading("Vendor Directory"), spacer(), count),
                bar(statusSwitch, search, clearSearch, edit, changeStatus),
                table,
                inspector);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(directory, Priority.ALWAYS);

        getChildren().setAll(
                pageHeading("Suppliers & Vendors",
                        "Manage procurement partners, contact channels, and purchasing availability.",
                        export, refresh, create),
                feedback,
                metrics,
                directory);

        if (!lastLoaded.isEmpty()) {
            updateCounts(lastLoaded, metricValues, metricSubs);
        }
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
            updateCounts(lastLoaded, metricValues, metricSubs);
            applyFilter.run();
            empty.setText(lastLoaded.isEmpty() ? "No suppliers yet. Choose + New supplier to add one."
                    : "No suppliers match your filters.");
            if (selectedId != null) {
                sorted.stream().filter(supplier -> supplier.id() == selectedId).findFirst()
                        .ifPresent(supplier -> {
                            table.getSelectionModel().select(supplier);
                            table.scrollTo(supplier);
                        });
            } else if (!sorted.isEmpty()) {
                table.getSelectionModel().selectFirst();
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
        address.setPromptText("Street, industrial zone, city, or delivery notes");
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

        Button cancel = button("Cancel", "supplier-secondary", () ->
                showDirectory(create ? null : supplier.id(), ""));
        cancel.disableProperty().bind(writing);

        Button back = button("Back to directory", "supplier-secondary", () ->
                showDirectory(create ? null : supplier.id(), ""));
        back.disableProperty().bind(writing);

        GridPane contact = new GridPane();
        contact.setHgap(14);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            contact.getColumnConstraints().add(column);
        }
        contact.addRow(0, field("Phone number", phone), field("Email address", email));

        Label recordBadge = create ? label("NEW VENDOR", "supplier-scope-pill") : badge(supplier.status());

        VBox form = card(
                bar(heading("Vendor Profile & Contact Details"), spacer(), recordBadge),
                field("Supplier name *", name),
                contact,
                field("Physical address & location notes", address),
                validation,
                bar(muted("* Required field"), spacer(), cancel, save));
        form.setMaxWidth(760);

        getChildren().setAll(
                pageHeading(create ? "New Supplier" : "Edit Supplier",
                        create ? "Register a new vendor to link with purchase orders."
                                : "Update contact channels and address details for " + supplier.name() + ".",
                        back),
                feedback,
                form);
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

    private void exportCsv(List<SupplierApiClient.Supplier> rowsToExport, Label feedback,
            Button exportBtn, Runnable onDone) {
        if (rowsToExport.isEmpty() || exporting) return;
        var chooser = new FileChooser();
        chooser.setTitle("Export Suppliers");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("suppliers-directory.csv");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        exporting = true;
        exportBtn.setDisable(true);
        Path destination = file.toPath();

        CompletableFuture.runAsync(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write("Supplier,Phone,Email,Address,Status\r\n");
                for (SupplierApiClient.Supplier s : rowsToExport) {
                    writer.write(csvCell(s.name()));
                    writer.write(',');
                    writer.write(csvCell(s.phone()));
                    writer.write(',');
                    writer.write(csvCell(s.email()));
                    writer.write(',');
                    writer.write(csvCell(s.address()));
                    writer.write(',');
                    writer.write(csvCell(s.active() ? "Active" : "Inactive"));
                    writer.write("\r\n");
                }
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }).whenComplete((unused, error) -> Platform.runLater(() -> {
            exporting = false;
            onDone.run();
            showFeedback(feedback, error == null
                    ? "Exported " + rowsToExport.size() + " suppliers to " + file.getName()
                    : "Couldn’t save CSV. Choose a writable folder.", error != null);
        }));
    }

    private static String csvCell(String text) {
        if (text == null) return "";
        String trimmed = text.stripLeading();
        if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
                || (!text.isEmpty() && "\t\r\n".indexOf(text.charAt(0)) >= 0)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
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
        boolean textMatch = List.of(
                value(supplier.name()),
                value(supplier.phone()),
                value(supplier.email()),
                value(supplier.address()))
                .stream()
                .anyMatch(field -> field.toLowerCase(Locale.ROOT).contains(term));
        String digits = term.replaceAll("\\D", "");
        return textMatch || (!digits.isEmpty() && term.matches("[+() .0-9-]+")
                && value(supplier.phone()).replaceAll("\\D", "").contains(digits));
    }

    private static void updateCounts(List<SupplierApiClient.Supplier> suppliers,
            EnumMap<Filter, Label> values, EnumMap<Filter, Label> subs) {
        int totalCount = suppliers.size();
        long activeCount = suppliers.stream().filter(SupplierApiClient.Supplier::active).count();
        long inactiveCount = suppliers.stream().filter(row -> "INACTIVE".equals(row.status())).count();
        long withEmail = suppliers.stream().filter(s -> s.email() != null && !s.email().isBlank()).count();

        values.get(Filter.ALL).setText(formatInt(totalCount));
        values.get(Filter.ACTIVE).setText(formatInt(activeCount));
        values.get(Filter.INACTIVE).setText(formatInt(inactiveCount));

        subs.get(Filter.ALL).setText(formatInt(withEmail) + " with email contact");
        subs.get(Filter.ACTIVE).setText("Ready for purchase orders");
        subs.get(Filter.INACTIVE).setText(inactiveCount == 0
                ? "All vendors currently active" : "Hidden from new purchase drafts");
    }

    private static void addSupplierColumn(TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, SupplierApiClient.Supplier> column = new TableColumn<>("Supplier");
        column.setPrefWidth(250);
        column.setMinWidth(180);
        column.setComparator(Comparator.comparing(SupplierApiClient.Supplier::name, String.CASE_INSENSITIVE_ORDER));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(SupplierApiClient.Supplier supplier, boolean empty) {
                super.updateItem(supplier, empty);
                setGraphic(null);
                setText(null);
                if (empty || supplier == null) return;

                StackPane avatar = avatarBadge(supplier.name(), supplier.active());
                Label nameLabel = new Label(display(supplier.name()));
                nameLabel.getStyleClass().add("supplier-cell-strong");
                HBox cellBox = new HBox(10, avatar, nameLabel);
                cellBox.setAlignment(Pos.CENTER_LEFT);
                setGraphic(cellBox);
                setTooltip(supplier.name() != null && supplier.name().length() >= 24
                        ? new Tooltip(supplier.name()) : null);
            }
        });
        table.getColumns().add(column);
    }

    private static void addContactColumn(TableView<SupplierApiClient.Supplier> table, String title,
            double width, Function<SupplierApiClient.Supplier, String> text, String styleClass) {
        TableColumn<SupplierApiClient.Supplier, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setMinWidth(120);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(display(text.apply(cell.getValue()))));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().remove(styleClass);
                if (empty || value == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(value);
                if (!"—".equals(value)) {
                    getStyleClass().add(styleClass);
                }
                setTooltip("—".equals(value) ? null : new Tooltip(value));
            }
        });
        table.getColumns().add(column);
    }

    private static void addAddressColumn(TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, String> column = new TableColumn<>("Address");
        column.setPrefWidth(220);
        column.setMinWidth(140);
        column.setCellValueFactory(cell -> {
            String raw = value(cell.getValue().address()).strip();
            String singleLine = raw.isEmpty() ? "—" : raw.replaceAll("\\s*\\R+\\s*", ", ");
            return new ReadOnlyStringWrapper(singleLine);
        });
        column.setCellFactory(ignored -> new TableCell<>() {
            { getStyleClass().add("supplier-cell-address"); }
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
        column.setPrefWidth(115);
        column.setMinWidth(100);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setGraphic(empty ? null : badge(status));
            }
        });
        table.getColumns().add(column);
    }

    private static StackPane avatarBadge(String name, boolean active) {
        Label letters = new Label(initials(name));
        letters.getStyleClass().add("supplier-avatar-text");
        StackPane circle = new StackPane(letters);
        circle.getStyleClass().addAll("supplier-avatar", active ? "supplier-avatar-active" : "supplier-avatar-inactive");
        circle.setMinSize(28, 28);
        circle.setPrefSize(28, 28);
        circle.setMaxSize(28, 28);
        return circle;
    }

    private static void updateAvatar(StackPane circle, String name, boolean active) {
        if (!circle.getChildren().isEmpty() && circle.getChildren().get(0) instanceof Label letters) {
            letters.setText(initials(name));
        }
        circle.getStyleClass().removeAll("supplier-avatar-active", "supplier-avatar-inactive");
        circle.getStyleClass().add(active ? "supplier-avatar-active" : "supplier-avatar-inactive");
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "—";
        String[] parts = name.strip().split("\\s+");
        if (parts.length == 1) {
            String word = parts[0];
            int end = word.offsetByCodePoints(0, Math.min(2, word.codePointCount(0, word.length())));
            return word.substring(0, end).toUpperCase(Locale.ROOT);
        }
        int firstEnd = parts[0].offsetByCodePoints(0, 1);
        int secondEnd = parts[1].offsetByCodePoints(0, 1);
        return (parts[0].substring(0, firstEnd) + parts[1].substring(0, secondEnd)).toUpperCase(Locale.ROOT);
    }

    private static Label badge(String status) {
        Label label = new Label();
        label.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        label.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        updateStatusBadge(label, status);
        return label;
    }

    private static void updateStatusBadge(Label label, String status) {
        boolean active = "ACTIVE".equals(status);
        boolean inactive = "INACTIVE".equals(status);
        label.setText(active ? "● Active" : inactive ? "● Inactive" : "Unknown");
        label.getStyleClass().setAll("label", "supplier-status",
                active ? "supplier-status-active" : "supplier-status-inactive");
    }

    private static VBox metricCard(String title, Filter filter,
            EnumMap<Filter, Label> values, EnumMap<Filter, Label> subs,
            EnumMap<Filter, VBox> cards, Consumer<Filter> onSelect) {
        Label heading = label(title, "supplier-metric-label");
        Label value = label("—", "supplier-metric-value");
        Label sub = label("Loading…", "supplier-metric-sub");
        values.put(filter, value);
        subs.put(filter, sub);

        VBox card = new VBox(4, heading, value, sub);
        card.getStyleClass().addAll("supplier-metric", "supplier-metric-" + filter.style);
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(e -> onSelect.accept(filter));
        Tooltip.install(card, new Tooltip("Click to filter by " + filter.label.toLowerCase(Locale.ROOT)));
        HBox.setHgrow(card, Priority.ALWAYS);
        cards.put(filter, card);
        return card;
    }

    private static String optional(String text) {
        String clean = value(text).strip();
        return clean.isEmpty() ? null : clean;
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static String display(String text) { return value(text).isBlank() ? "—" : text; }

    private static String formatInt(long value) {
        return NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value);
    }

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
        Label label = label(title, "supplier-field-label");
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

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static Button button(String title, String css, Runnable action) {
        Button button = new Button(title);
        button.getStyleClass().add(css);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
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
