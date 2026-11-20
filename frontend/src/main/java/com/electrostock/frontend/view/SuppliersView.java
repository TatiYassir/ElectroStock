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
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.electrostock.frontend.api.SupplierApiClient;
import com.electrostock.frontend.api.SupplierApiClient.SupplierInput;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.util.Duration;

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

    private static final int CONTACT_FIELDS = 3;
    private static final int AVATAR_TONES = 6;
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}");

    private final SupplierApiClient api = new SupplierApiClient();
    private final SimpleBooleanProperty writing = new SimpleBooleanProperty();
    private List<SupplierApiClient.Supplier> lastLoaded = List.of();
    private long screen;
    private String searchText = "";
    private Filter currentFilter = Filter.ALL;
    private boolean incompleteOnly;
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

    private void showDirectory(Long selectedId, String noticeText) {
        long page = ++screen;
        var notice = new Notice();
        if (!noticeText.isEmpty()) notice.toast(noticeText);

        var rows = FXCollections.observableArrayList(lastLoaded);
        var shown = new FilteredList<>(rows);
        var sorted = new SortedList<>(shown);

        TableView<SupplierApiClient.Supplier> table = new TableView<>();
        table.getStyleClass().add("supplier-table");
        table.setAccessibleText("Supplier directory");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(240);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(loadingState());

        TableColumn<SupplierApiClient.Supplier, SupplierApiClient.Supplier> nameColumn = addSupplierColumn(table);
        addContactColumn(table);
        addAddressColumn(table);
        addProfileColumn(table);
        addStatusColumn(table);
        nameColumn.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().add(nameColumn);

        Label count = label("Loading…", "supplier-count-pill");

        TextField search = new TextField(searchText);
        search.getStyleClass().add("supplier-search");
        search.setPromptText("Search name, phone, email or address…");
        search.setAccessibleText("Search suppliers by name, phone, email, or address");
        search.setMinWidth(160);
        HBox.setHgrow(search, Priority.ALWAYS);

        Button clearSearch = button("✕", "supplier-chip-btn", search::clear);
        clearSearch.setTooltip(new Tooltip("Clear the search (Esc)"));
        clearSearch.setFocusTraversable(false);
        show(clearSearch, !searchText.isEmpty());

        ToggleButton incomplete = new ToggleButton("Needs contact info");
        incomplete.getStyleClass().add("supplier-chip-toggle");
        incomplete.setSelected(incompleteOnly);
        incomplete.setFocusTraversable(false);
        incomplete.setTooltip(new Tooltip("Show only vendors missing a phone number, email or address."));

        Button export = button("Export CSV", "supplier-ghost-light", () -> {});
        export.setTooltip(new Tooltip("Export the suppliers currently in view to CSV."));
        export.setDisable(true);

        var metricValues = new EnumMap<Filter, Label>(Filter.class);
        var metricSubs = new EnumMap<Filter, Label>(Filter.class);
        var metricMeters = new EnumMap<Filter, ProgressBar>(Filter.class);
        var metricCards = new EnumMap<Filter, VBox>(Filter.class);
        var filterTabs = new EnumMap<Filter, ToggleButton>(Filter.class);

        Label coverage = muted("Contact coverage —");

        Runnable syncFilterUi = () -> {
            ToggleButton activeTab = filterTabs.get(currentFilter);
            if (activeTab != null && !activeTab.isSelected()) {
                activeTab.setSelected(true);
            }
            metricCards.forEach((filter, card) -> {
                card.getStyleClass().remove("supplier-metric-selected");
                if (filter == currentFilter) {
                    card.getStyleClass().add("supplier-metric-selected");
                }
            });
        };

        Runnable applyFilter = () -> {
            searchText = value(search.getText());
            show(clearSearch, !searchText.isEmpty());
            incompleteOnly = incomplete.isSelected();
            shown.setPredicate(supplier -> currentFilter.matches(supplier)
                    && (!incompleteOnly || completenessCount(supplier) < CONTACT_FIELDS)
                    && matches(supplier, searchText));
            count.setText(shown.size() + " of " + rows.size()
                    + (rows.size() == 1 ? " supplier" : " suppliers"));
            coverage.setText(coverageText(shown));
            export.setDisable(exporting || writing.get() || shown.isEmpty());
            syncFilterUi.run();
        };

        Consumer<Filter> selectFilter = chosen -> {
            currentFilter = chosen == null ? Filter.ALL : chosen;
            applyFilter.run();
        };

        VBox totalCard = metricCard("TOTAL SUPPLIERS", Filter.ALL, metricValues, metricSubs, metricMeters, metricCards, selectFilter);
        VBox activeCard = metricCard("ACTIVE VENDORS", Filter.ACTIVE, metricValues, metricSubs, metricMeters, metricCards, selectFilter);
        VBox pausedCard = metricCard("PAUSED VENDORS", Filter.INACTIVE, metricValues, metricSubs, metricMeters, metricCards, selectFilter);
        HBox metrics = new HBox(14, totalCard, activeCard, pausedCard);

        HBox statusSwitch = new HBox(2);
        statusSwitch.getStyleClass().add("supplier-seg-group");
        statusSwitch.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup statusGroup = new ToggleGroup();
        for (Filter filter : Filter.values()) {
            ToggleButton chip = new ToggleButton(filter.chipLabel);
            chip.setToggleGroup(statusGroup);
            chip.setFocusTraversable(false);
            chip.getStyleClass().addAll("supplier-seg-btn", "supplier-seg-" + filter.style);
            if (filter == currentFilter) chip.setSelected(true);
            chip.setOnAction(event -> selectFilter.accept(filter));
            filterTabs.put(filter, chip);
            statusSwitch.getChildren().add(chip);
        }
        statusGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) oldToggle.setSelected(true);
        });

        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());
        incomplete.selectedProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());

        export.setOnAction(event -> exportCsv(List.copyOf(table.getItems()), notice, export,
                () -> export.setDisable(exporting || writing.get() || shown.isEmpty())));

        var selection = table.getSelectionModel().selectedItemProperty();

        // ---- Inspector panel -------------------------------------------------
        StackPane inspectorAvatar = avatar("—", true, 54);
        ((Label) inspectorAvatar.getChildren().get(0)).getStyleClass().add("supplier-avatar-text-lg");
        Label inspectorName = label("", "supplier-inspector-name");
        Label inspectorId = muted("");
        Label inspectorStatus = statusChip("ACTIVE");
        Label phoneValue = label("", "supplier-inspector-value");
        Label emailValue = label("", "supplier-inspector-value");
        Label addressValue = label("", "supplier-inspector-value");
        addressValue.setWrapText(true);
        ProgressBar inspectorMeter = meter();
        inspectorMeter.setMinWidth(0);
        inspectorMeter.setMaxWidth(Double.MAX_VALUE);
        Label inspectorMeterText = muted("");

        VBox inspectorHead = new VBox(9, inspectorAvatar, inspectorName, inspectorId,
                bar(inspectorStatus));
        inspectorHead.getStyleClass().add("supplier-inspector-head");

        Button edit = button("Edit supplier", "supplier-primary", () -> {
            var supplier = selection.get();
            if (supplier != null) openEdit(supplier.id());
        });
        edit.disableProperty().bind(writing.or(selection.isNull()));
        edit.setMaxWidth(Double.MAX_VALUE);

        Button changeStatus = button("Deactivate", "supplier-danger", () -> {
            var supplier = selection.get();
            if (supplier != null) changeStatus(supplier, notice);
        });
        changeStatus.disableProperty().bind(writing.or(selection.isNull()));
        changeStatus.setMaxWidth(Double.MAX_VALUE);

        Region inspectorGrow = new Region();
        VBox.setVgrow(inspectorGrow, Priority.ALWAYS);

        VBox inspector = new VBox(13,
                inspectorHead,
                sectionLabel("Contact channels"),
                contactRow("Phone", phoneValue,
                        () -> copy(selection.get() == null ? null : selection.get().phone(), "Phone number", notice)),
                contactRow("Email", emailValue,
                        () -> copy(selection.get() == null ? null : selection.get().email(), "Email address", notice)),
                contactRow("Address", addressValue,
                        () -> copy(selection.get() == null ? null : selection.get().address(), "Address", notice)),
                sectionLabel("Profile completeness"),
                new VBox(6, inspectorMeter, inspectorMeterText),
                inspectorGrow,
                edit,
                changeStatus);
        inspector.getStyleClass().add("supplier-inspector");
        inspector.setMinWidth(298);
        inspector.setPrefWidth(298);
        inspector.setMaxWidth(298);
        inspector.visibleProperty().bind(selection.isNotNull());
        inspector.managedProperty().bind(inspector.visibleProperty());

        selection.addListener((obs, previous, supplier) -> {
            boolean deactivate = supplier != null && supplier.active();
            changeStatus.setText(supplier == null || deactivate ? "Deactivate" : "Reactivate");
            changeStatus.getStyleClass().removeAll("supplier-danger", "supplier-secondary");
            changeStatus.getStyleClass().add(deactivate ? "supplier-danger" : "supplier-secondary");
            if (supplier == null) return;

            updateAvatar(inspectorAvatar, supplier.name(), supplier.active());
            inspectorName.setText(display(supplier.name()));
            inspectorId.setText("Vendor #" + supplier.id());
            updateStatusChip(inspectorStatus, supplier.status());
            setContact(phoneValue, supplier.phone(), "No phone number recorded");
            setContact(emailValue, supplier.email(), "No email address recorded");
            setContact(addressValue, singleLine(supplier.address()), "No physical address recorded");
            inspectorMeter.setProgress(completeness(supplier));
            inspectorMeterText.setText(completenessCount(supplier) + " of " + CONTACT_FIELDS
                    + " contact details on file");
        });

        table.setRowFactory(ignored -> {
            TableRow<SupplierApiClient.Supplier> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    openEdit(row.getItem().id());
                }
            });
            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) return;
                var supplier = row.getItem();
                var menu = new ContextMenu();
                var editItem = new MenuItem("Edit supplier");
                editItem.setOnAction(action -> openEdit(supplier.id()));
                var copyEmail = new MenuItem("Copy email address");
                copyEmail.setOnAction(action -> copy(supplier.email(), "Email address", notice));
                var copyPhone = new MenuItem("Copy phone number");
                copyPhone.setOnAction(action -> copy(supplier.phone(), "Phone number", notice));
                var toggleItem = new MenuItem(supplier.active() ? "Deactivate supplier" : "Reactivate supplier");
                toggleItem.setOnAction(action -> changeStatus(supplier, notice));
                menu.getItems().addAll(editItem, copyEmail, copyPhone, toggleItem);
                menu.show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                edit.fire();
                event.consume();
            }
        });

        HBox searchRow = bar(search, clearSearch);
        searchRow.getStyleClass().add("supplier-search-row");
        HBox.setHgrow(searchRow, Priority.ALWAYS);

        HBox toolbar = bar(searchRow, statusSwitch, incomplete);
        toolbar.getStyleClass().add("supplier-toolbar");

        VBox directory = card(
                bar(heading("Vendor directory"), count, spacer(), muted("Double-click a row to edit")),
                toolbar,
                table,
                bar(coverage, spacer(), muted("Right-click a row for quick actions")));
        directory.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(directory, Priority.ALWAYS);

        HBox workspace = new HBox(16, directory, inspector);
        workspace.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(directory, Priority.ALWAYS);
        VBox.setVgrow(workspace, Priority.ALWAYS);

        Button refresh = button("Refresh", "supplier-ghost-light", () -> {
            var picked = selection.get();
            showDirectory(picked == null ? null : picked.id(), "");
        });
        refresh.disableProperty().bind(writing);
        Button create = button("+ New supplier", "supplier-inverse", () -> showForm(null));
        create.disableProperty().bind(writing);

        VBox hero = hero("PROCUREMENT",
                "Supplier network",
                "Manage vendors, contact channels and purchasing availability across your catalogue.",
                export, refresh, create);

        VBox content = new VBox(16, hero, notice.banner, metrics, workspace);
        content.getStyleClass().add("supplier-page-body");
        content.setPadding(new Insets(22));
        content.setFocusTraversable(true);
        content.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F && event.isShortcutDown()) {
                search.requestFocus();
                search.selectAll();
                event.consume();
            } else if (event.getCode() == KeyCode.N && event.isShortcutDown()) {
                if (!writing.get()) showForm(null);
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && !search.getText().isEmpty()) {
                search.clear();
                event.consume();
            }
        });

        StackPane layer = new StackPane(content, notice.toasts);
        StackPane.setAlignment(notice.toasts, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(notice.toasts, new Insets(0, 26, 26, 0));
        getChildren().setAll(layer);
        VBox.setVgrow(layer, Priority.ALWAYS);

        if (!lastLoaded.isEmpty()) updateCounts(lastLoaded, metricValues, metricSubs, metricMeters);
        applyFilter.run();

        api.list().whenComplete((suppliers, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                notice.error((noticeText.isEmpty() ? "" : noticeText + " ") + errorMessage(error)
                        + (lastLoaded.isEmpty() ? "" : " Showing the last loaded list."));
                table.setPlaceholder(emptyState(lastLoaded.isEmpty()
                        ? "Suppliers could not be loaded. Check the backend and refresh."
                        : "No suppliers match your filters."));
                if (lastLoaded.isEmpty()) count.setText("Not loaded");
                return;
            }
            lastLoaded = suppliers.stream()
                    .sorted(Comparator.comparing(SupplierApiClient.Supplier::name, String.CASE_INSENSITIVE_ORDER)
                            .thenComparingLong(SupplierApiClient.Supplier::id)).toList();
            rows.setAll(lastLoaded);
            updateCounts(lastLoaded, metricValues, metricSubs, metricMeters);
            applyFilter.run();
            table.setPlaceholder(emptyState(lastLoaded.isEmpty()
                    ? "No suppliers yet. Choose + New supplier to register your first vendor."
                    : "No suppliers match your filters."));
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
        var notice = new Notice();
        Button back = button("← Back to suppliers", "supplier-ghost-light", this::onOpen);
        notice.info("Loading supplier…");
        getChildren().setAll(pageShell(
                hero("VENDOR PROFILE", "Edit supplier",
                        "Update the contact channels and address used for purchasing.",
                        back),
                notice));
        api.get(id).whenComplete((supplier, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error == null) {
                showForm(supplier);
                return;
            }
            Button retry = button("Try again", "supplier-primary", () -> openEdit(id));
            VBox failure = card(
                    heading("Supplier could not be loaded"),
                    muted(errorMessage(error)),
                    bar(retry, back));
            failure.setMaxWidth(620);
            getChildren().setAll(pageShell(
                    hero("VENDOR PROFILE", "Edit supplier",
                            "Update the contact channels and address used for purchasing.",
                            back),
                    new Notice(), failure));
        }));
    }

    private void showForm(SupplierApiClient.Supplier supplier) {
        showForm(supplier, "");
    }

    private void showForm(SupplierApiClient.Supplier supplier, String noticeText) {
        ++screen;
        boolean create = supplier == null;
        var notice = new Notice();
        if (!noticeText.isEmpty()) notice.toast(noticeText);

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

        Label counter = label(nameCounter(name.getText()), "supplier-counter");

        Label validation = muted("");
        validation.getStyleClass().add("supplier-validation");
        validation.setWrapText(true);
        validation.visibleProperty().bind(validation.textProperty().isNotEmpty());
        validation.managedProperty().bind(validation.visibleProperty());

        var invalid = new SimpleBooleanProperty(true);
        Supplier<SupplierInput> input = () -> new SupplierInput(value(name.getText()).strip(),
                optional(phone.getText()), optional(email.getText()), optional(address.getText()));
        Runnable validate = () -> {
            SupplierInput data = input.get();
            String issue = inputProblem(data);
            invalid.set(issue != null);
            validation.setText(issue == null ? "" : issue);
            markInvalid(name, data.name().isBlank() || tooLong(data.name(), 200));
            markInvalid(phone, data.phone() != null && !phoneOk(data.phone()));
            markInvalid(email, data.email() != null && !emailOk(data.email()));
            counter.setText(nameCounter(data.name()));
        };
        for (var field : List.of(name, phone, email, address)) {
            field.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        }
        validate.run();

        Button save = button(create ? "Create supplier" : "Save changes", "supplier-primary", () -> {
            SupplierInput data = input.get();
            write("Saving supplier…", notice, () -> create ? api.create(data) : api.update(supplier.id(), data),
                    saved -> showDirectory(saved.id(), create ? "Supplier created." : "Supplier details updated."));
        });
        save.disableProperty().bind(writing.or(invalid));
        save.setDefaultButton(true);

        Button saveAnother = button("Save & add another", "supplier-secondary", () -> {
            SupplierInput data = input.get();
            write("Saving supplier…", notice, () -> api.create(data),
                    saved -> showForm(null, "Supplier created. Ready for the next one."));
        });
        saveAnother.disableProperty().bind(writing.or(invalid));
        show(saveAnother, create);

        Button cancel = button("Cancel", "supplier-secondary", () ->
                showDirectory(create ? null : supplier.id(), ""));
        cancel.disableProperty().bind(writing);

        Button back = button("← Back to directory", "supplier-ghost-light", () ->
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

        VBox formCard = card(
                heading("Vendor profile"),
                muted("Fields marked with an asterisk are required. Contact details are optional but improve order routing."),
                new VBox(6, bar(label("Supplier name *", "supplier-field-label"), spacer(), counter), name),
                contact,
                field("Physical address & location notes", address),
                validation,
                bar(muted("* Required field"), spacer(), cancel, saveAnother, save));
        formCard.setMaxWidth(Double.MAX_VALUE);

        // ---- Live preview ----------------------------------------------------
        StackPane previewAvatar = avatar(create ? "" : supplier.name(), create || supplier.active(), 58);
        ((Label) previewAvatar.getChildren().get(0)).getStyleClass().add("supplier-avatar-text-lg");
        Label previewName = label(create ? "New vendor" : display(supplier.name()), "supplier-preview-name");
        Label previewId = muted(create ? "Not saved yet" : "Vendor #" + supplier.id());
        Label previewStatus = create ? draftChip() : statusChip(supplier.status());
        ProgressBar previewMeter = meter();
        Label previewMeterText = muted("0 of " + CONTACT_FIELDS + " contact details");
        Label previewHint = muted("Complete contact details so purchasing can reach this vendor about prices, delays and deliveries.");
        previewHint.setWrapText(true);

        Runnable refreshPreview = () -> {
            String typed = value(name.getText()).strip();
            updateAvatar(previewAvatar, typed, true);
            previewName.setText(typed.isEmpty() ? "New vendor" : typed);
            int filled = 0;
            if (!value(phone.getText()).isBlank()) filled++;
            if (!value(email.getText()).isBlank()) filled++;
            if (!value(address.getText()).isBlank()) filled++;
            previewMeter.setProgress(filled / (double) CONTACT_FIELDS);
            previewMeterText.setText(filled + " of " + CONTACT_FIELDS + " contact details");
        };
        for (Control field : List.of(name, phone, email, address)) {
            if (field instanceof TextField textField) {
                textField.textProperty().addListener((obs, oldValue, newValue) -> refreshPreview.run());
            } else if (field instanceof TextArea textArea) {
                textArea.textProperty().addListener((obs, oldValue, newValue) -> refreshPreview.run());
            }
        }
        refreshPreview.run();

        VBox previewCard = card(
                sectionLabel("Live preview"),
                new VBox(9, previewAvatar, previewName, previewId, bar(previewStatus)),
                new VBox(6, previewMeter, previewMeterText),
                previewHint);
        previewCard.getStyleClass().add("supplier-preview");
        previewCard.setMinWidth(290);
        previewCard.setPrefWidth(290);
        previewCard.setMaxWidth(290);

        HBox formRow = new HBox(16, formCard, previewCard);
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formRow.setMaxWidth(1180);

        Label recordBadge = create ? label("NEW VENDOR", "supplier-hero-badge") : label("EDITING", "supplier-hero-badge");
        Label formSub = label(create ? "Add a purchasing partner so it can be used on purchase orders."
                : "Update contact channels and address details for " + display(supplier.name()) + ".",
                "supplier-hero-sub");
        formSub.setWrapText(true);
        formSub.setMaxWidth(720);

        VBox formHero = new VBox(10,
                bar(back, spacer(), recordBadge),
                label("VENDOR RECORD", "supplier-hero-kicker"),
                label(create ? "Register a vendor" : "Edit vendor", "supplier-hero-title"),
                formSub);
        formHero.getStyleClass().add("supplier-hero");

        VBox content = new VBox(16, formHero, notice.banner, formRow);
        content.getStyleClass().add("supplier-page-body");
        content.setPadding(new Insets(22));
        content.setFocusTraversable(true);
        content.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && !writing.get()) {
                showDirectory(create ? null : supplier.id(), "");
                event.consume();
            } else if (event.getCode() == KeyCode.S && event.isShortcutDown() && !save.isDisabled()) {
                save.fire();
                event.consume();
            }
        });

        StackPane layer = new StackPane(content, notice.toasts);
        StackPane.setAlignment(notice.toasts, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(notice.toasts, new Insets(0, 26, 26, 0));
        getChildren().setAll(layer);
        VBox.setVgrow(layer, Priority.ALWAYS);
        Platform.runLater(name::requestFocus);
    }

    private void changeStatus(SupplierApiClient.Supplier supplier, Notice notice) {
        if (writing.get()) return;
        boolean target = !supplier.active();
        String action = target ? "Reactivate" : "Deactivate";
        String description = target
                ? "The supplier will be available for new purchase drafts again."
                : "The supplier will be hidden from the active supplier list for new drafts. Existing purchases are kept.";
        ButtonType accept = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, description, ButtonType.CANCEL, accept);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Suppliers");
        dialog.setHeaderText(action + " " + supplier.name() + "?");
        if (dialog.showAndWait().filter(accept::equals).isEmpty()) return;
        write("Updating supplier status…", notice, () -> api.setActive(supplier.id(), target),
                saved -> showDirectory(saved.id(), target ? "Supplier reactivated." : "Supplier deactivated."));
    }

    private <T> void write(String pending, Notice notice,
            Supplier<CompletableFuture<T>> request, Consumer<T> success) {
        if (writing.get()) return;
        long page = screen;
        writing.set(true);
        notice.info(pending);
        try {
            request.get().whenComplete((result, error) -> Platform.runLater(() -> {
                writing.set(false);
                if (page != screen) return;
                if (error != null) notice.error(errorMessage(error));
                else success.accept(result);
            }));
        } catch (RuntimeException e) {
            writing.set(false);
            notice.error(errorMessage(e));
        }
    }

    private void exportCsv(List<SupplierApiClient.Supplier> rowsToExport, Notice notice,
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
        notice.info("Writing " + rowsToExport.size() + " suppliers to " + file.getName() + "…");

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
            if (error == null) {
                notice.clear();
                notice.toast("Exported " + rowsToExport.size() + " suppliers to " + file.getName());
            } else {
                notice.error("Couldn’t save CSV. Choose a writable folder.");
            }
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
        String name = value(input.name()).strip();
        if (name.isEmpty()) return "Enter the supplier name.";
        if (tooLong(name, 200)) return "Supplier names can contain up to 200 characters.";
        if (tooLong(input.phone(), 30)) return "Phone numbers can contain up to 30 characters.";
        if (input.phone() != null && !phoneOk(input.phone())) {
            return "Use digits, spaces, +, -, ( and ) for the phone number, with at least 4 digits.";
        }
        if (tooLong(input.email(), 254)) return "Email addresses can contain up to 254 characters.";
        if (input.email() != null && !emailOk(input.email())) {
            return "Enter a valid email address, for example orders@example.com.";
        }
        if (input.address() != null && input.address().getBytes(StandardCharsets.UTF_8).length > 65_535) {
            return "The address is too long.";
        }
        return null;
    }

    private static boolean phoneOk(String phone) {
        String clean = value(phone).strip();
        return clean.matches("[+() .0-9-]+") && clean.replaceAll("\\D", "").length() >= 4;
    }

    private static boolean emailOk(String email) {
        return EMAIL.matcher(value(email).strip()).matches();
    }

    private static boolean tooLong(String text, int limit) {
        return text != null && text.codePointCount(0, text.length()) > limit;
    }

    private static String nameCounter(String text) {
        int length = value(text).strip().codePointCount(0, value(text).strip().length());
        return length + " / 200";
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

    private static int completenessCount(SupplierApiClient.Supplier supplier) {
        int filled = 0;
        if (!value(supplier.phone()).isBlank()) filled++;
        if (!value(supplier.email()).isBlank()) filled++;
        if (!value(supplier.address()).isBlank()) filled++;
        return filled;
    }

    private static double completeness(SupplierApiClient.Supplier supplier) {
        return completenessCount(supplier) / (double) CONTACT_FIELDS;
    }

    private static String coverageText(List<? extends SupplierApiClient.Supplier> suppliers) {
        if (suppliers.isEmpty()) return "No suppliers in this view";
        int filled = 0;
        int complete = 0;
        for (var supplier : suppliers) {
            int count = completenessCount(supplier);
            filled += count;
            if (count == CONTACT_FIELDS) complete++;
        }
        int percent = Math.round(filled * 100f / (suppliers.size() * CONTACT_FIELDS));
        int incomplete = suppliers.size() - complete;
        return percent + "% contact coverage · " + (incomplete == 0
                ? "every profile is complete" : incomplete + " profile" + (incomplete == 1 ? "" : "s") + " need details");
    }

    private static void updateCounts(List<SupplierApiClient.Supplier> suppliers,
            EnumMap<Filter, Label> values, EnumMap<Filter, Label> subs,
            EnumMap<Filter, ProgressBar> meters) {
        int totalCount = suppliers.size();
        long activeCount = suppliers.stream().filter(SupplierApiClient.Supplier::active).count();
        long inactiveCount = suppliers.stream().filter(row -> "INACTIVE".equals(row.status())).count();
        long withEmail = suppliers.stream().filter(s -> !value(s.email()).isBlank()).count();

        values.get(Filter.ALL).setText(formatInt(totalCount));
        values.get(Filter.ACTIVE).setText(formatInt(activeCount));
        values.get(Filter.INACTIVE).setText(formatInt(inactiveCount));

        subs.get(Filter.ALL).setText(formatInt(withEmail) + " with email on file");
        subs.get(Filter.ACTIVE).setText(activeCount == 0 ? "No active vendors yet" : "Ready for purchase orders");
        subs.get(Filter.INACTIVE).setText(inactiveCount == 0
                ? "All vendors currently active" : "Hidden from new purchase drafts");

        meters.get(Filter.ALL).setProgress(totalCount == 0 ? 0 : 1);
        meters.get(Filter.ACTIVE).setProgress(totalCount == 0 ? 0 : activeCount / (double) totalCount);
        meters.get(Filter.INACTIVE).setProgress(totalCount == 0 ? 0 : inactiveCount / (double) totalCount);
    }

    private static TableColumn<SupplierApiClient.Supplier, SupplierApiClient.Supplier> addSupplierColumn(
            TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, SupplierApiClient.Supplier> column = new TableColumn<>("Supplier");
        column.setPrefWidth(260);
        column.setMinWidth(180);
        column.setComparator(Comparator.comparing(supplier -> value(supplier.name()), String.CASE_INSENSITIVE_ORDER));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(SupplierApiClient.Supplier supplier, boolean empty) {
                super.updateItem(supplier, empty);
                setText(null);
                setGraphic(null);
                if (empty || supplier == null) return;

                StackPane badge = avatar(supplier.name(), supplier.active(), 34);
                Label nameLabel = label(display(supplier.name()), "supplier-cell-strong");
                Label idLabel = label("Vendor #" + supplier.id(), "supplier-cell-sub");
                VBox text = new VBox(2, nameLabel, idLabel);
                text.setMinWidth(0);
                HBox cellBox = new HBox(11, badge, text);
                cellBox.setAlignment(Pos.CENTER_LEFT);
                setGraphic(cellBox);
                setTooltip(supplier.name() != null && supplier.name().length() >= 24
                        ? new Tooltip(supplier.name()) : null);
            }
        });
        table.getColumns().add(column);
        return column;
    }

    private static void addContactColumn(TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, SupplierApiClient.Supplier> column = new TableColumn<>("Contact");
        column.setPrefWidth(235);
        column.setMinWidth(170);
        column.setComparator(Comparator.comparing(supplier -> value(supplier.email()),
                String.CASE_INSENSITIVE_ORDER));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Label phone = label("", "supplier-contact-phone");
            private final Label email = label("", "supplier-contact-email");
            private final VBox box = new VBox(2, phone, email);

            { box.setAlignment(Pos.CENTER_LEFT); }

            @Override protected void updateItem(SupplierApiClient.Supplier supplier, boolean empty) {
                super.updateItem(supplier, empty);
                setText(null);
                if (empty || supplier == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                boolean hasPhone = !value(supplier.phone()).isBlank();
                boolean hasEmail = !value(supplier.email()).isBlank();
                styleEmpty(phone, hasPhone, hasPhone ? supplier.phone() : "No phone number");
                styleEmpty(email, hasEmail, hasEmail ? supplier.email() : "No email address");
                setGraphic(box);
                setTooltip((hasPhone || hasEmail) ? new Tooltip(
                        (hasPhone ? supplier.phone() : "No phone") + "\n"
                                + (hasEmail ? supplier.email() : "No email")) : null);
            }
        });
        table.getColumns().add(column);
    }

    private static void addAddressColumn(TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, String> column = new TableColumn<>("Address");
        column.setPrefWidth(200);
        column.setMinWidth(130);
        column.setComparator(String.CASE_INSENSITIVE_ORDER);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                singleLine(cell.getValue().address()).isEmpty() ? "—" : singleLine(cell.getValue().address())));
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

    private static void addProfileColumn(TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, SupplierApiClient.Supplier> column = new TableColumn<>("Profile");
        column.setPrefWidth(150);
        column.setMinWidth(130);
        column.setComparator(Comparator.comparingDouble(supplier -> completeness(supplier)));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            private final ProgressBar progress = meter();
            private final Label text = label("", "supplier-meter-text");
            private final HBox box = new HBox(8, progress, text);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                Tooltip.install(box, new Tooltip("Phone, email and address on file."));
            }

            @Override protected void updateItem(SupplierApiClient.Supplier supplier, boolean empty) {
                super.updateItem(supplier, empty);
                setText(null);
                if (empty || supplier == null) {
                    setGraphic(null);
                    return;
                }
                progress.setProgress(completeness(supplier));
                text.setText(completenessCount(supplier) + "/" + CONTACT_FIELDS);
                setGraphic(box);
            }
        });
        table.getColumns().add(column);
    }

    private static void addStatusColumn(TableView<SupplierApiClient.Supplier> table) {
        TableColumn<SupplierApiClient.Supplier, String> column = new TableColumn<>("Status");
        column.setPrefWidth(125);
        column.setMinWidth(108);
        column.setComparator(Comparator.comparing((String status) -> "ACTIVE".equals(status) ? 0 : 1));
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status()));
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Label chip = statusChip("");
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(null);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                updateStatusChip(chip, status);
                setGraphic(chip);
            }
        });
        table.getColumns().add(column);
    }

    private static StackPane avatar(String name, boolean active, double size) {
        Label letters = label(initials(name), "supplier-avatar-text");
        StackPane circle = new StackPane(letters);
        circle.getStyleClass().addAll("supplier-avatar", tone(name),
                active ? "supplier-avatar-active" : "supplier-avatar-inactive");
        circle.setMinSize(size, size);
        circle.setPrefSize(size, size);
        circle.setMaxSize(size, size);
        return circle;
    }

    private static void updateAvatar(StackPane circle, String name, boolean active) {
        if (!circle.getChildren().isEmpty() && circle.getChildren().get(0) instanceof Label letters) {
            letters.setText(initials(name));
        }
        circle.getStyleClass().removeIf(style -> style.startsWith("supplier-avatar-tone-"));
        circle.getStyleClass().add(tone(name));
        circle.getStyleClass().removeAll("supplier-avatar-active", "supplier-avatar-inactive");
        circle.getStyleClass().add(active ? "supplier-avatar-active" : "supplier-avatar-inactive");
    }

    private static String tone(String name) {
        return "supplier-avatar-tone-" + Math.floorMod(value(name).hashCode(), AVATAR_TONES);
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

    private static Label statusChip(String status) {
        Label chip = new Label();
        chip.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        chip.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        updateStatusChip(chip, status);
        return chip;
    }

    private static void updateStatusChip(Label chip, String status) {
        boolean active = "ACTIVE".equals(status);
        boolean inactive = "INACTIVE".equals(status);
        chip.setText(active ? "● Active" : inactive ? "● Inactive" : "Unknown");
        chip.getStyleClass().setAll("label", "supplier-status",
                active ? "supplier-status-active" : "supplier-status-inactive");
    }

    private static Label draftChip() {
        Label chip = new Label("◆ Draft");
        chip.getStyleClass().addAll("supplier-status", "supplier-status-draft");
        chip.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return chip;
    }

    private static VBox metricCard(String kicker, Filter filter,
            EnumMap<Filter, Label> values, EnumMap<Filter, Label> subs,
            EnumMap<Filter, ProgressBar> meters, EnumMap<Filter, VBox> cards,
            Consumer<Filter> onSelect) {
        Label kick = label(kicker, "supplier-metric-kicker");
        Label value = label("—", "supplier-metric-value");
        Label sub = label("Loading…", "supplier-metric-sub");
        ProgressBar progress = meter();
        progress.setProgress(0);
        progress.setMinWidth(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        values.put(filter, value);
        subs.put(filter, sub);
        meters.put(filter, progress);

        VBox card = new VBox(6, kick, value, sub, progress);
        card.getStyleClass().addAll("supplier-metric", "supplier-metric-" + filter.style);
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(event -> onSelect.accept(filter));
        Tooltip.install(card, new Tooltip("Click to filter by " + filter.label.toLowerCase(Locale.ROOT)));
        HBox.setHgrow(card, Priority.ALWAYS);
        cards.put(filter, card);
        return card;
    }

    private static VBox contactRow(String title, Label value, Runnable onCopy) {
        Label caption = label(title.toUpperCase(Locale.ROOT), "supplier-inspector-label");
        Button copy = button("Copy", "supplier-mini-btn", onCopy);
        copy.setFocusTraversable(false);
        VBox row = new VBox(3, bar(caption, spacer(), copy), value);
        row.getStyleClass().add("supplier-inspector-row");
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private static Label sectionLabel(String text) {
        return label(text.toUpperCase(Locale.ROOT), "supplier-section-label");
    }

    private static ProgressBar meter() {
        ProgressBar bar = new ProgressBar(0);
        bar.getStyleClass().add("supplier-meter");
        bar.setPrefHeight(6);
        bar.setMinHeight(6);
        bar.setMaxHeight(6);
        bar.setPrefWidth(74);
        bar.setFocusTraversable(false);
        bar.setMouseTransparent(true);
        return bar;
    }

    private static void styleEmpty(Label label, boolean filled, String text) {
        label.setText(text);
        label.getStyleClass().remove("supplier-empty");
        if (!filled) label.getStyleClass().add("supplier-empty");
    }

    private static void setContact(Label label, String text, String emptyText) {
        boolean empty = value(text).isBlank();
        label.setText(empty ? emptyText : text.strip());
        label.getStyleClass().remove("supplier-empty");
        if (empty) label.getStyleClass().add("supplier-empty");
    }

    private static VBox loadingState() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("supplier-spinner");
        spinner.setPrefSize(28, 28);
        spinner.setMaxSize(28, 28);
        VBox box = new VBox(10, spinner, muted("Loading suppliers…"));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("supplier-placeholder");
        return box;
    }

    private static VBox emptyState(String message, Node... extra) {
        Label glyph = label("◇", "supplier-placeholder-glyph");
        Label text = muted(message);
        text.setWrapText(true);
        text.setMaxWidth(360);
        text.setTextAlignment(TextAlignment.CENTER);
        VBox box = new VBox(10, glyph, text);
        box.getChildren().addAll(extra);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("supplier-placeholder");
        return box;
    }

    private static VBox hero(String kicker, String title, String subtitle, Node... actions) {
        Label kick = label(kicker, "supplier-hero-kicker");
        Label head = label(title, "supplier-hero-title");
        Label sub = label(subtitle, "supplier-hero-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(620);
        VBox copy = new VBox(6, kick, head, sub);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox row = new HBox(24, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        if (actions.length > 0) {
            HBox actionBox = new HBox(10, actions);
            actionBox.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(actionBox);
        }
        VBox box = new VBox(row);
        box.getStyleClass().add("supplier-hero");
        return box;
    }

    private static StackPane pageShell(Node hero, Notice notice, Node... body) {
        VBox content = new VBox(16, hero, notice.banner);
        content.getChildren().addAll(body);
        content.getStyleClass().add("supplier-page-body");
        content.setPadding(new Insets(22));
        StackPane layer = new StackPane(content, notice.toasts);
        StackPane.setAlignment(notice.toasts, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(notice.toasts, new Insets(0, 26, 26, 0));
        VBox.setVgrow(layer, Priority.ALWAYS);
        return layer;
    }

    private static void copy(String text, String what, Notice notice) {
        if (value(text).isBlank()) {
            notice.toast(what + " is not recorded for this vendor.");
            return;
        }
        var content = new ClipboardContent();
        content.putString(text.strip());
        Clipboard.getSystemClipboard().setContent(content);
        notice.toast(what + " copied to the clipboard.");
    }

    private static void markInvalid(Control control, boolean invalid) {
        control.getStyleClass().remove("supplier-input-invalid");
        if (invalid) control.getStyleClass().add("supplier-input-invalid");
    }

    private static String optional(String text) {
        String clean = value(text).strip();
        return clean.isEmpty() ? null : clean;
    }

    private static String value(String text) { return text == null ? "" : text; }

    private static String display(String text) { return value(text).isBlank() ? "—" : text.strip(); }

    private static String singleLine(String text) {
        String raw = value(text).strip();
        return raw.isEmpty() ? "" : raw.replaceAll("\\s*\\R+\\s*", ", ");
    }

    private static String formatInt(long value) {
        return NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value);
    }

    private static String errorMessage(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        if (error instanceof ConnectException) return "Could not reach the backend. Check that Spring Boot is running.";
        if (error instanceof HttpTimeoutException) return "The backend did not respond in time. Refresh before retrying a change.";
        return error.getMessage() == null || error.getMessage().isBlank() ? "The request failed." : error.getMessage();
    }

    private static VBox card(Node... children) {
        VBox box = new VBox(13, children);
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
        VBox box = new VBox(6, label, control);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Label heading(String text) {
        return label(text, "supplier-card-title");
    }

    private static Label muted(String text) {
        return label(text, "supplier-muted");
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

    /** Inline notice banner plus a floating toast stack for transient confirmations. */
    private static final class Notice {
        private final Label banner = feedback();
        private final HBox toasts = new HBox(8);

        private Notice() {
            toasts.setAlignment(Pos.BOTTOM_RIGHT);
            toasts.setPickOnBounds(false);
            toasts.setMouseTransparent(true);
        }

        private void info(String text) { showFeedback(banner, text, false); }

        private void error(String text) { showFeedback(banner, text, true); }

        private void clear() { showFeedback(banner, "", false); }

        private void toast(String text) {
            if (text == null || text.isBlank()) return;
            Label pill = new Label(text);
            pill.getStyleClass().add("supplier-toast");
            pill.setOpacity(0);
            toasts.getChildren().add(pill);
            while (toasts.getChildren().size() > 3) toasts.getChildren().remove(0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), pill);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            PauseTransition hold = new PauseTransition(Duration.seconds(2.6));
            FadeTransition fadeOut = new FadeTransition(Duration.millis(280), pill);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            var sequence = new SequentialTransition(fadeIn, hold, fadeOut);
            sequence.setOnFinished(event -> toasts.getChildren().remove(pill));
            sequence.play();
        }
    }
}
