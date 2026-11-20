package com.electrostock.frontend.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.electrostock.frontend.api.SaleApiClient;
import com.electrostock.frontend.api.SaleApiClient.Currency;
import com.electrostock.frontend.api.SaleApiClient.Line;
import com.electrostock.frontend.api.SaleApiClient.Sale;
import com.electrostock.frontend.api.SaleApiClient.Summary;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
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

/** The Sales page, including persisted drafts and the full sale lifecycle. */
public class SalesView extends VBox {
    private enum Status {
        ALL("All statuses", ""),
        DRAFT("Draft", "draft"),
        AWAITING("Awaiting handover", "awaiting"),
        HANDED("Handed over", "handed"),
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

    /** Fixed transaction date pill: set automatically, not editable by the user. */
    private static final String FIXED_DATE_STYLE =
            "-fx-background-color: linear-gradient(to right, #F0FDFA 0%, #F8FAFC 100%);"
            + "-fx-background-radius: 10;"
            + "-fx-border-color: #99F6E4;"
            + "-fx-border-radius: 10;"
            + "-fx-border-width: 1;"
            + "-fx-text-fill: #0F766E;"
            + "-fx-font-size: 13px;"
            + "-fx-font-weight: bold;"
            + "-fx-padding: 9 14;";

    private final SaleApiClient api = new SaleApiClient();
    private final SaleDialogs dialogs = new SaleDialogs(api);
    private final SimpleBooleanProperty writing = new SimpleBooleanProperty();
    private long screen;
    private String searchText = "";
    private Status selectedStatus = Status.ALL;

    public SalesView() {
        getStyleClass().add("sales-page");
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
        Label empty = new Label("Loading sales…");
        table.setPlaceholder(empty);

        addTypedColumn(table, "Sale #", 105, false, Summary::id, id -> "#" + id,
                Comparator.naturalOrder(), "sale-cell-strong");
        addTypedColumn(table, "Sale date", 155, false, Summary::saleDate, SalesView::date,
                Comparator.naturalOrder());
        addStatusColumn(table);
        addTagColumn(table, "Currency", 115, Summary::currencyCode, "sale-currency-tag");
        addTypedColumn(table, "Total", 170, true, Summary::totalCurrency, SalesView::amount,
                Comparator.naturalOrder(), "sale-cell-amount");

        Button open = button("Open sale", "sale-secondary", () -> {
            Summary picked = table.getSelectionModel().getSelectedItem();
            if (picked != null) loadSale(picked.id());
        });
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        table.setRowFactory(ignored -> {
            TableRow<Summary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    loadSale(row.getItem().id());
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) open.fire();
        });

        TextField search = new TextField(searchText);
        search.setPromptText("Search by sale #, currency, status, or date…");
        search.setAccessibleText("Search sales");
        HBox.setHgrow(search, Priority.ALWAYS);

        ComboBox<Status> status = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
        status.setAccessibleText("Filter by sale status");
        status.setValue(selectedStatus);

        Button resetFilters = button("Reset", "sale-secondary", () -> {
            search.clear();
            status.setValue(Status.ALL);
        });
        resetFilters.managedProperty().bind(resetFilters.visibleProperty());

        Label count = small("Loading…");

        Label allCount = new Label("—");
        Label draftCount = new Label("—");
        Label awaitingCount = new Label("—");
        Label handedCount = new Label("—");

        VBox kpiAll = kpiCard("Total sales", allCount, "All recorded orders",
                () -> status.setValue(Status.ALL));
        VBox kpiDraft = kpiCard("Drafts", draftCount, "Open & editable",
                () -> status.setValue(selectedStatus == Status.DRAFT ? Status.ALL : Status.DRAFT));
        VBox kpiAwaiting = kpiCard("Awaiting handover", awaitingCount, "Stock reserved",
                () -> status.setValue(selectedStatus == Status.AWAITING ? Status.ALL : Status.AWAITING));
        VBox kpiHanded = kpiCard("Handed over", handedCount, "Completed deliveries",
                () -> status.setValue(selectedStatus == Status.HANDED ? Status.ALL : Status.HANDED));

        HBox kpiBar = new HBox(12, kpiAll, kpiDraft, kpiAwaiting, kpiHanded);

        Runnable syncKpiStyles = () -> {
            setKpiActive(kpiAll, selectedStatus == Status.ALL);
            setKpiActive(kpiDraft, selectedStatus == Status.DRAFT);
            setKpiActive(kpiAwaiting, selectedStatus == Status.AWAITING);
            setKpiActive(kpiHanded, selectedStatus == Status.HANDED);
        };

        Runnable applyFilters = () -> {
            searchText = search.getText() == null ? "" : search.getText();
            selectedStatus = status.getValue() == null ? Status.ALL : status.getValue();
            String query = searchText.strip().replaceFirst("^#", "").toLowerCase(Locale.ROOT);

            filtered.setPredicate(row -> {
                Status rowStatus = statusOf(row.status(), row.handedOverAt());
                if (selectedStatus != Status.ALL && selectedStatus != rowStatus) {
                    return false;
                }
                if (query.isEmpty()) {
                    return true;
                }
                return Long.toString(row.id()).contains(query)
                        || row.currencyCode().toLowerCase(Locale.ROOT).contains(query)
                        || rowStatus.text.toLowerCase(Locale.ROOT).contains(query)
                        || date(row.saleDate()).toLowerCase(Locale.ROOT).contains(query);
            });

            count.setText("Showing " + filtered.size() + " of " + data.size() + " sales");
            resetFilters.setVisible(!query.isEmpty() || selectedStatus != Status.ALL);
            syncKpiStyles.run();
        };

        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        status.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());

        Button refresh = button("Refresh", "sale-secondary", this::showList);
        Button create = button("+ New sale", "sale-primary", this::showNewSale);
        create.disableProperty().bind(writing);

        HBox filters = bar(search, status, resetFilters, open);
        VBox history = card(bar(heading("Sales history"), spacer(), count), filters, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(history, Priority.ALWAYS);

        getChildren().setAll(
                pageHeading("Sales", "Create draft orders, deduct stock on confirmation, and record customer handovers.", refresh, create),
                feedback,
                kpiBar,
                history);

        api.list().whenComplete((rows, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                count.setText("Not loaded");
                empty.setText("Sales could not be loaded.");
                showFeedback(feedback, message(error), true);
            } else {
                data.setAll(rows);
                long drafts = rows.stream().filter(r -> statusOf(r.status(), r.handedOverAt()) == Status.DRAFT).count();
                long awaiting = rows.stream().filter(r -> statusOf(r.status(), r.handedOverAt()) == Status.AWAITING).count();
                long handed = rows.stream().filter(r -> statusOf(r.status(), r.handedOverAt()) == Status.HANDED).count();

                allCount.setText(String.valueOf(rows.size()));
                draftCount.setText(String.valueOf(drafts));
                awaitingCount.setText(String.valueOf(awaiting));
                handedCount.setText(String.valueOf(handed));

                applyFilters.run();
                empty.setText(rows.isEmpty()
                        ? "No sales yet. Click \"+ New sale\" to create your first order."
                        : "No sales match your current filters.");
            }
        }));
    }

    public void showNewSale() {
        long page = ++screen;
        Label feedback = feedback();

        Label stepEyebrow = new Label("STEP 1 OF 2  ·  ORDER SETUP");
        stepEyebrow.getStyleClass().add("sale-eyebrow");

        Label cardTitle = new Label("Create a new sale draft");
        cardTitle.getStyleClass().add("sale-setup-title");

        Label cardSub = small("Billing currency and today's date are applied before adding products.");
        cardSub.setWrapText(true);

        // The sale date is not user-controlled: every draft lands on today.
        LocalDate saleDate = LocalDate.now();

        Label dateValue = new Label(date(saleDate) + "  ·  Today");
        dateValue.setStyle(FIXED_DATE_STYLE);
        dateValue.setMaxWidth(Double.MAX_VALUE);

        Label dateNote = small("The sale date is set to today automatically and cannot be changed.");
        dateNote.setWrapText(true);

        VBox dateRow = new VBox(6, dateValue, dateNote);

        ComboBox<Currency> currency = new ComboBox<>();
        currency.setPromptText("Loading currencies…");
        currency.setMaxWidth(Double.MAX_VALUE);

        Label currencyHint = small("Foreign currency sales lock the live MAD exchange rate when confirmed.");
        currencyHint.setWrapText(true);

        VBox callout = new VBox(3,
                heading("How draft sales work"),
                small("Drafts are saved automatically and do not reserve or deduct inventory until you confirm the order."));
        callout.getStyleClass().add("sale-callout-info");
        callout.getChildren().forEach(node -> {
            if (node instanceof Label lbl) lbl.setWrapText(true);
        });

        Button cancelBtn = button("Cancel", "sale-secondary", this::showList);
        Button submit = button("Create draft & add items →", "sale-primary", () ->
                write("Creating draft…", feedback,
                        () -> api.createDraft(currency.getValue().code(), saleDate),
                        id -> loadSale(id, "Draft created. Add products below before confirming.")));
        submit.disableProperty().bind(writing.or(currency.valueProperty().isNull()));

        HBox footerActions = bar(cancelBtn, spacer(), submit);

        VBox form = new VBox(16,
                new VBox(4, stepEyebrow, cardTitle, cardSub),
                new Separator(),
                field("Sale date", dateRow),
                new VBox(5, field("Checkout currency", currency), currencyHint),
                callout,
                footerActions);
        form.getStyleClass().add("sale-setup-card");
        form.setMaxWidth(560);
        form.setPrefWidth(560);

        VBox centerStage = new VBox(form);
        centerStage.setAlignment(Pos.CENTER);
        centerStage.setFillWidth(false);
        VBox.setVgrow(centerStage, Priority.ALWAYS);

        Button back = button("← Back to sales", "sale-secondary", this::showList);

        getChildren().setAll(
                pageHeading("New sale", "Set up a new customer order.", back),
                feedback,
                centerStage);

        api.currencies().whenComplete((currencies, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                currency.setPromptText("Currencies could not be loaded");
                showFeedback(feedback, message(error), true);
            } else {
                currency.getItems().setAll(currencies);
                currency.setPromptText("Choose a currency");
                currencies.stream().filter(c -> "MAD".equals(c.code())).findFirst()
                        .ifPresent(currency::setValue);
                if (currencies.isEmpty()) showFeedback(feedback, "No currencies are configured.", true);
            }
        }));
    }

    private void loadSale(long id) {
        loadSale(id, "");
    }

    private void loadSale(long id, String notice) {
        long page = ++screen;
        Button back = button("← Back to sales", "sale-secondary", this::showList);
        Label feedback = feedback();
        showFeedback(feedback, "Loading sale #" + id + "…", false);
        getChildren().setAll(pageHeading("Sale #" + id, "Sale details", back), feedback);
        api.get(id).whenComplete((sale, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                showFeedback(feedback, (notice.isEmpty() ? "" : notice + " ") + message(error), true);
                getChildren().add(button("Retry", "sale-secondary", () -> loadSale(id, notice)));
            } else {
                renderSale(sale, notice);
            }
        }));
    }

    private void renderSale(Sale sale, String notice) {
        Label feedback = feedback();
        if (!notice.isEmpty()) showFeedback(feedback, notice, false);
        Status status = statusOf(sale.status(), sale.handedOverAt());

        Button back = button("← Back to sales", "sale-secondary", this::showList);
        Button refresh = button("Refresh", "sale-secondary", () -> loadSale(sale.id()));
        refresh.disableProperty().bind(writing);

        VBox summary = card(
                bar(heading("Sale overview"), spacer(), badge(status)),
                lifecycleStepper(status));

        HBox figures = bar(
                metric("Sale date", date(sale.saleDate())),
                metric("Currency", sale.currencyCode()),
                metric("Order total", money(sale.totalCurrency(), sale.currencyCode())),
                metric("MAD equivalent", sale.totalMad() == null ? "Pending confirmation" : money(sale.totalMad(), "MAD")));
        summary.getChildren().add(figures);

        if (!"MAD".equals(sale.currencyCode()) && sale.rateToMadSnapshot() != null) {
            String rateText = "Exchange rate: 1 " + sale.currencyCode() + " = "
                    + sale.rateToMadSnapshot().stripTrailingZeros().toPlainString() + " MAD";
            if (sale.rateSourceSnapshot() != null && !sale.rateSourceSnapshot().isBlank()) {
                rateText += "  ·  Source: " + sale.rateSourceSnapshot();
            }
            if (sale.rateFromCacheSnapshot()) {
                rateText += " (cached)";
            }
            summary.getChildren().add(small(rateText));
        } else if (sale.rateFromCacheSnapshot()) {
            summary.getChildren().add(small("Confirmed using a cached exchange rate."));
        }

        if (sale.handedOverAt() != null) {
            summary.getChildren().add(small("Handed over to customer on " + DATE_TIME.format(sale.handedOverAt())));
        }

        TableView<Line> lines = table();
        lines.getItems().setAll(sale.items());
        lines.setPlaceholder(new Label(status == Status.DRAFT
                ? "No items in this draft yet. Click \"+ Add product\" to begin."
                : "No line items recorded."));

        addTagColumn(lines, "SKU", 130, line -> fallback(line.sku()), "sale-sku-cell-tag");
        addTypedColumn(lines, "Product", 220, false,
                line -> line.productName() == null ? "Product #" + line.productId() : line.productName(),
                Function.identity(), String.CASE_INSENSITIVE_ORDER, "sale-cell-strong");
        addTypedColumn(lines, "Qty", 80, true, Line::quantity, String::valueOf, Comparator.naturalOrder());
        addTypedColumn(lines, "Unit price", 130, true, Line::unitPriceCurrency, SalesView::amount, Comparator.naturalOrder());
        addTypedColumn(lines, "Line total", 140, true, Line::lineTotalCurrency, SalesView::amount,
                Comparator.naturalOrder(), "sale-cell-amount");
        addColumn(lines, "Serial numbers", 190, false, line ->
                line.serialNumbers() == null || line.serialNumbers().isEmpty()
                        ? (line.tracksSerials() && status == Status.DRAFT ? "Assigned at confirmation" : "—")
                        : String.join(", ", line.serialNumbers()));

        Runnable openEditDialog = () -> {
            Line selected = lines.getSelectionModel().getSelectedItem();
            if (selected == null || status != Status.DRAFT) return;
            dialogs.editLine(getScene().getWindow(), sale, selected).ifPresent(updated ->
                    write("Updating item…", feedback,
                            () -> api.updateLine(sale.id(), selected.id(), updated.productId(), updated.quantity(), updated.unitPrice()),
                            ignored -> loadSale(sale.id(), "Line item updated.")));
        };

        if (status == Status.DRAFT) {
            lines.setRowFactory(ignored -> {
                TableRow<Line> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                        openEditDialog.run();
                    }
                });
                return row;
            });
        }

        int totalUnits = sale.items().stream().mapToInt(Line::quantity).sum();
        HBox itemsFooter = bar(
                small(sale.items().size() + " line(s)  ·  " + totalUnits + " total unit(s)"),
                spacer(),
                heading("Subtotal:  " + money(sale.totalCurrency(), sale.currencyCode())));
        itemsFooter.getStyleClass().add("sale-items-footer");

        HBox lineHeader = bar(heading("Line items"), spacer());
        HBox actions = bar();

        if (status == Status.DRAFT) {
            Button edit = button("Edit selected", "sale-secondary", openEditDialog);
            Button remove = button("Remove selected", "sale-secondary", () -> {
                Line selected = lines.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    write("Removing product…", feedback,
                            () -> api.removeLine(sale.id(), selected.id()),
                            ignored -> loadSale(sale.id(), "Product removed from the draft."));
                }
            });
            Button add = button("+ Add product", "sale-secondary", () ->
                    dialogs.addLine(getScene().getWindow(), sale).ifPresent(line ->
                            write("Adding product…", feedback,
                                    () -> api.addLine(sale.id(), line.productId(), line.quantity(), line.unitPrice()),
                                    ignored -> loadSale(sale.id(), "Product added to the draft."))));

            Button discard = button("Discard draft", "sale-danger", () -> {
                if (approve("DISCARD DRAFT",
                        "Discard draft sale #" + sale.id() + "?",
                        "This draft and all its line items will be permanently deleted. This action cannot be undone.",
                        "Discard draft",
                        true)) {
                    write("Discarding draft…", feedback,
                            () -> api.deleteDraft(sale.id()),
                            ignored -> showList());
                }
            });

            Button confirm = button("Confirm sale", "sale-primary", () ->
                    dialogs.confirm(getScene().getWindow(), sale).ifPresent(serials ->
                            write("Confirming sale…", feedback,
                                    () -> api.confirm(sale.id(), serials),
                                    ignored -> loadSale(sale.id(), "Sale confirmed and stock deducted."))));
            confirm.getStyleClass().add("sale-confirm-action");

            edit.disableProperty().bind(writing.or(lines.getSelectionModel().selectedItemProperty().isNull()));
            remove.disableProperty().bind(writing.or(lines.getSelectionModel().selectedItemProperty().isNull()));
            add.disableProperty().bind(writing);
            discard.disableProperty().bind(writing);
            confirm.disableProperty().bind(writing.or(new SimpleBooleanProperty(sale.items().isEmpty())));

            lineHeader.getChildren().addAll(edit, remove, add);
            actions.getChildren().addAll(
                    discard,
                    spacer(),
                    small("Verify prices & quantities before confirming."),
                    confirm);
        } else if (status == Status.AWAITING) {
            Button cancel = button("Cancel sale", "sale-danger", () -> {
                if (approve("CANCEL ORDER",
                        "Cancel sale #" + sale.id() + "?",
                        "All stock deducted for this sale will be returned to available inventory.",
                        "Cancel sale",
                        true)) {
                    write("Cancelling sale…", feedback,
                            () -> api.cancel(sale.id()),
                            ignored -> loadSale(sale.id(), "Sale cancelled and stock restored."));
                }
            });
            Button handover = button("Mark handed over", "sale-primary", () -> {
                if (approve("CUSTOMER HANDOVER",
                        "Mark sale #" + sale.id() + " as handed over?",
                        "Confirm only after the customer has received the items. Warranty timers will start immediately and the sale cannot be cancelled afterward.",
                        "Mark handed over",
                        false)) {
                    write("Recording handover…", feedback,
                            () -> api.handOver(sale.id()),
                            ignored -> loadSale(sale.id(), "Handover recorded."));
                }
            });
            cancel.disableProperty().bind(writing);
            handover.disableProperty().bind(writing);
            actions.getChildren().addAll(
                    small("Stock is reserved while awaiting customer handover."),
                    spacer(),
                    cancel,
                    handover);
        }

        VBox itemsCard = card(lineHeader, lines, itemsFooter);
        if (!actions.getChildren().isEmpty()) {
            itemsCard.getChildren().add(actions);
        }
        VBox.setVgrow(lines, Priority.ALWAYS);
        VBox.setVgrow(itemsCard, Priority.ALWAYS);

        getChildren().setAll(
                pageHeading("Sale #" + sale.id(), "Review line items, totals, and fulfillment status.", back, refresh),
                feedback,
                summary,
                itemsCard);
    }

    private static HBox lifecycleStepper(Status status) {
        HBox stepper = new HBox(8);
        stepper.getStyleClass().add("sale-stepper");
        stepper.setAlignment(Pos.CENTER_LEFT);

        if (status == Status.CANCELLED) {
            stepper.getChildren().addAll(
                    stepPill("1. Draft", "sale-step-done"),
                    stepArrow(),
                    stepPill("2. Confirmed", "sale-step-done"),
                    stepArrow(),
                    stepPill("3. Cancelled (Stock Restored)", "sale-step-cancelled"));
            return stepper;
        }

        String s1 = status == Status.DRAFT ? "sale-step-current" : "sale-step-done";
        String s2 = status == Status.DRAFT ? "sale-step-pending"
                : status == Status.AWAITING ? "sale-step-current" : "sale-step-done";
        String s3 = status == Status.HANDED ? "sale-step-done" : "sale-step-pending";

        stepper.getChildren().addAll(
                stepPill("1. Draft", s1),
                stepArrow(),
                stepPill("2. Confirmed & Stock Deducted", s2),
                stepArrow(),
                stepPill("3. Handed Over", s3));
        return stepper;
    }

    private static Label stepPill(String text, String stateClass) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("sale-step", stateClass);
        return pill;
    }

    private static Label stepArrow() {
        Label arrow = new Label("→");
        arrow.getStyleClass().add("sale-muted");
        return arrow;
    }

    private static VBox kpiCard(String title, Label valueLabel, String hint, Runnable onClick) {
        Label head = small(title);
        valueLabel.getStyleClass().add("sale-kpi-value");
        Label sub = small(hint);

        VBox card = new VBox(4, head, valueLabel, sub);
        card.getStyleClass().add("sale-kpi-card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setOnMouseClicked(e -> onClick.run());
        return card;
    }

    private static void setKpiActive(VBox card, boolean active) {
        card.getStyleClass().remove("sale-kpi-card-active");
        if (active) {
            card.getStyleClass().add("sale-kpi-card-active");
        }
    }

    private boolean approve(String eyebrow, String title, String detail, String action, boolean danger) {
        ButtonType accept = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("ElectroStock · Sales");

        var pane = dialog.getDialogPane();
        applyStyles(pane);
        pane.getStyleClass().add("sales-dialog");
        pane.setPrefWidth(480);

        Label tag = new Label(eyebrow);
        tag.getStyleClass().add(danger ? "sale-eyebrow-danger" : "sale-eyebrow");

        Label head = new Label(title);
        head.getStyleClass().add("sale-confirm-dialog-title");
        head.setWrapText(true);

        Label body = new Label(detail);
        body.getStyleClass().add("sale-confirm-dialog-body");
        body.setWrapText(true);

        VBox callout = new VBox(body);
        callout.getStyleClass().add(danger ? "sale-callout-danger" : "sale-callout-amber");

        VBox content = new VBox(12, new VBox(4, tag, head), callout);
        content.setPadding(new Insets(6, 0, 2, 0));
        pane.setContent(content);

        pane.getButtonTypes().setAll(ButtonType.CANCEL, accept);
        Node confirmBtn = pane.lookupButton(accept);
        if (confirmBtn != null) {
            confirmBtn.getStyleClass().add(danger ? "sale-danger-solid" : "sale-confirm-action");
        }
        Node cancelBtn = pane.lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) {
            cancelBtn.getStyleClass().add("sale-secondary");
        }

        dialog.setResultConverter(pressed -> pressed == accept);
        return dialog.showAndWait().orElse(false);
    }

    private <T> void write(String pending, Label feedback, Supplier<CompletableFuture<T>> request, Consumer<T> success) {
        if (writing.get()) return;
        long page = screen;
        writing.set(true);
        showFeedback(feedback, pending, false);
        try {
            request.get().whenComplete((value, error) -> Platform.runLater(() -> {
                writing.set(false);
                if (page != screen) return;
                if (error != null) {
                    showFeedback(feedback, message(error), true);
                } else {
                    success.accept(value);
                }
            }));
        } catch (RuntimeException e) {
            writing.set(false);
            showFeedback(feedback, message(e), true);
        }
    }

    private static Status statusOf(String status, LocalDateTime handedOverAt) {
        return switch (status) {
            case "DRAFT" -> Status.DRAFT;
            case "CANCELLED" -> Status.CANCELLED;
            case "CONFIRMED" -> handedOverAt == null ? Status.AWAITING : Status.HANDED;
            default -> Status.ALL;
        };
    }

    private static HBox badge(Status status) {
        String key = status == Status.ALL ? "draft" : status.css;
        Region dot = new Region();
        dot.getStyleClass().addAll("sale-status-dot", "sale-status-dot-" + key);
        dot.setMinSize(6, 6);
        dot.setPrefSize(6, 6);
        dot.setMaxSize(6, 6);

        Label text = new Label(status == Status.ALL ? "Unknown" : status.text);
        text.getStyleClass().addAll("sale-status-text", "sale-status-text-" + key);

        HBox pill = new HBox(6, dot, text);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.getStyleClass().addAll("sale-status", "sale-status-" + key);
        return pill;
    }

    private static void addStatusColumn(TableView<Summary> table) {
        TableColumn<Summary, Status> column = new TableColumn<>("Status");
        column.setPrefWidth(195);
        column.setMinWidth(180);
        column.setComparator(Comparator.naturalOrder());
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                statusOf(cell.getValue().status(), cell.getValue().handedOverAt())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(Status status, boolean empty) {
                super.updateItem(status, empty);
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(empty || status == null ? null : badge(status));
            }
        });
        table.getColumns().add(column);
    }

    private static <T> void addTagColumn(TableView<T> table, String title, double width,
            Function<T, String> extractor, String tagStyle) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setMinWidth(width - 15);
        column.setComparator(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(extractor.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
                if (empty || value == null || value.isBlank() || "—".equals(value)) {
                    setGraphic(null);
                    if (!empty && value != null) setText(value);
                } else {
                    Label pill = new Label(value);
                    pill.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                    pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                    pill.getStyleClass().add(tagStyle);
                    setGraphic(pill);
                }
            }
        });
        table.getColumns().add(column);
    }

    private static <T> void addColumn(TableView<T> table, String title, double width,
            boolean numeric, Function<T, String> text) {
        addTypedColumn(table, title, width, numeric, text, Function.identity(), String.CASE_INSENSITIVE_ORDER);
    }

    private static <T, V> void addTypedColumn(TableView<T> table, String title, double width,
            boolean numeric, Function<T, V> extractor, Function<V, String> formatter,
            Comparator<V> comparator, String... extraCellClasses) {
        TableColumn<T, V> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        if (numeric) {
            column.getStyleClass().add("sale-col-numeric");
        }
        column.setComparator(Comparator.nullsLast(comparator));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(extractor.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(V value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll(extraCellClasses);
                String formatted = empty || value == null ? null : formatter.apply(value);
                setText(formatted);
                setAlignment(numeric ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                setTooltip(formatted == null ? null : new Tooltip(formatted));
                if (!empty && value != null && extraCellClasses.length > 0) {
                    getStyleClass().addAll(extraCellClasses);
                }
            }
        });
        table.getColumns().add(column);
    }

    private static <T> TableView<T> table() {
        TableView<T> table = new TableView<>();
        table.getStyleClass().add("sale-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(140);
        return table;
    }

    private static HBox pageHeading(String title, String subtitle, Node... actions) {
        Label head = new Label(title);
        head.getStyleClass().add("sale-page-title");
        HBox row = bar(new VBox(3, head, small(subtitle)), spacer());
        row.getChildren().addAll(actions);
        row.getStyleClass().add("sale-hero");
        return row;
    }

    private static VBox metric(String name, String value) {
        Label number = new Label(value);
        number.getStyleClass().add("sale-metric-value");
        VBox box = new VBox(5, small(name), number);
        box.getStyleClass().add("sale-metric");
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("sale-card");
        return box;
    }

    static void applyStyles(Parent parent) {
        var css = SalesView.class.getResource("sales.css");
        if (css != null) parent.getStylesheets().add(css.toExternalForm());
    }

    static HBox bar(Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static VBox field(String title, javafx.scene.control.Control control) {
        Label label = new Label(title);
        label.getStyleClass().add("sale-field-label");
        label.setLabelFor(control);
        return new VBox(5, label, control);
    }

    static VBox field(String title, Node control) {
        Label label = new Label(title);
        label.getStyleClass().add("sale-field-label");
        return new VBox(5, label, control);
    }

    static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sale-card-title");
        return label;
    }

    static Label small(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sale-muted");
        return label;
    }

    static String formatDate(LocalDate date) {
        return date(date);
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
        label.getStyleClass().setAll("label", error ? "sale-error" : "sale-notice");
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
