package com.electrostock.frontend.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.electrostock.frontend.api.PurchaseApiClient;
import com.electrostock.frontend.api.PurchaseApiClient.Currency;
import com.electrostock.frontend.api.PurchaseApiClient.Line;
import com.electrostock.frontend.api.PurchaseApiClient.Purchase;
import com.electrostock.frontend.api.PurchaseApiClient.Summary;

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
import javafx.scene.control.DatePicker;
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

/** The Purchases page, including persisted drafts and the full purchase lifecycle. */
public class PurchasesView extends VBox {
    private enum Status {
        ALL("All statuses", ""),
        DRAFT("Draft", "draft"),
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

    private record DraftOptions(List<PurchaseApiClient.Supplier> suppliers, List<Currency> currencies) {}

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

        addTypedColumn(table, "Purchase #", 110, false, Summary::id, id -> "#" + id,
                Comparator.naturalOrder(), "purchase-cell-strong");
        addTypedColumn(table, "Purchase date", 145, false, Summary::purchaseDate, PurchasesView::date,
                Comparator.naturalOrder());
        addTypedColumn(table, "Supplier", 200, false, row -> fallback(row.supplierName()),
                Function.identity(), String.CASE_INSENSITIVE_ORDER, "purchase-cell-strong");
        addStatusColumn(table);
        addTagColumn(table, "Currency", 110, Summary::currencyCode, "purchase-currency-tag");
        addTypedColumn(table, "Total", 165, true, Summary::totalCurrency, PurchasesView::amount,
                Comparator.naturalOrder(), "purchase-cell-amount");

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
        search.setPromptText("Search by purchase #, supplier, currency, status, or date…");
        search.setAccessibleText("Search purchases");
        HBox.setHgrow(search, Priority.ALWAYS);

        ComboBox<Status> status = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
        status.setAccessibleText("Filter by purchase status");
        status.setValue(selectedStatus);

        Button resetFilters = button("Reset", "purchase-secondary", () -> {
            search.clear();
            status.setValue(Status.ALL);
        });
        resetFilters.managedProperty().bind(resetFilters.visibleProperty());

        Label count = small("Loading…");

        Label allCount = new Label("—");
        Label draftCount = new Label("—");
        Label confirmedCount = new Label("—");
        Label cancelledCount = new Label("—");

        VBox kpiAll = kpiCard("Total purchases", allCount, "All recorded orders",
                () -> status.setValue(Status.ALL));
        VBox kpiDraft = kpiCard("Drafts", draftCount, "Open & editable",
                () -> status.setValue(selectedStatus == Status.DRAFT ? Status.ALL : Status.DRAFT));
        VBox kpiConfirmed = kpiCard("Confirmed", confirmedCount, "Stock added",
                () -> status.setValue(selectedStatus == Status.CONFIRMED ? Status.ALL : Status.CONFIRMED));
        VBox kpiCancelled = kpiCard("Cancelled", cancelledCount, "Receipts reversed",
                () -> status.setValue(selectedStatus == Status.CANCELLED ? Status.ALL : Status.CANCELLED));

        HBox kpiBar = new HBox(12, kpiAll, kpiDraft, kpiConfirmed, kpiCancelled);

        Runnable syncKpiStyles = () -> {
            setKpiActive(kpiAll, selectedStatus == Status.ALL);
            setKpiActive(kpiDraft, selectedStatus == Status.DRAFT);
            setKpiActive(kpiConfirmed, selectedStatus == Status.CONFIRMED);
            setKpiActive(kpiCancelled, selectedStatus == Status.CANCELLED);
        };

        Runnable applyFilters = () -> {
            searchText = search.getText() == null ? "" : search.getText();
            selectedStatus = status.getValue() == null ? Status.ALL : status.getValue();
            String query = searchText.strip().replaceFirst("^#", "").strip().toLowerCase(Locale.ROOT);

            filtered.setPredicate(row -> {
                Status rowStatus = statusOf(row.status());
                if (selectedStatus != Status.ALL && selectedStatus != rowStatus) {
                    return false;
                }
                if (query.isEmpty()) {
                    return true;
                }
                return Long.toString(row.id()).contains(query)
                        || fallback(row.supplierName()).toLowerCase(Locale.ROOT).contains(query)
                        || row.currencyCode().toLowerCase(Locale.ROOT).contains(query)
                        || rowStatus.text.toLowerCase(Locale.ROOT).contains(query)
                        || date(row.purchaseDate()).toLowerCase(Locale.ROOT).contains(query);
            });

            count.setText("Showing " + filtered.size() + " of " + data.size() + " purchases");
            resetFilters.setVisible(!query.isEmpty() || selectedStatus != Status.ALL);
            syncKpiStyles.run();
        };

        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        status.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());

        Button refresh = button("Refresh", "purchase-secondary", this::showList);
        Button create = button("+ New purchase", "purchase-primary", this::showNewPurchase);
        create.disableProperty().bind(writing);

        HBox filters = bar(search, status, resetFilters, open);
        VBox history = card(bar(heading("Purchases history"), spacer(), count), filters, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(history, Priority.ALWAYS);

        getChildren().setAll(
                pageHeading("Purchases", "Record supplier orders, receive inventory on confirmation, and track purchase history.", refresh, create),
                feedback,
                kpiBar,
                history);

        api.list().whenComplete((rows, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                count.setText("Not loaded");
                empty.setText("Purchases could not be loaded.");
                showFeedback(feedback, message(error), true);
            } else {
                data.setAll(rows);
                long drafts = rows.stream().filter(r -> statusOf(r.status()) == Status.DRAFT).count();
                long confirmed = rows.stream().filter(r -> statusOf(r.status()) == Status.CONFIRMED).count();
                long cancelled = rows.stream().filter(r -> statusOf(r.status()) == Status.CANCELLED).count();

                allCount.setText(String.valueOf(rows.size()));
                draftCount.setText(String.valueOf(drafts));
                confirmedCount.setText(String.valueOf(confirmed));
                cancelledCount.setText(String.valueOf(cancelled));

                applyFilters.run();
                empty.setText(rows.isEmpty()
                        ? "No purchases yet. Click \"+ New purchase\" to create your first order."
                        : "No purchases match your current filters.");
            }
        }));
    }

    public void showNewPurchase() {
        long page = ++screen;
        Label feedback = feedback();

        Label stepEyebrow = new Label("STEP 1 OF 2  ·  PURCHASE SETUP");
        stepEyebrow.getStyleClass().add("purchase-eyebrow");

        Label cardTitle = new Label("Create a new purchase draft");
        cardTitle.getStyleClass().add("purchase-setup-title");

        Label cardSub = small("Select the supplier, invoice date, and billing currency before adding line items.");
        cardSub.setWrapText(true);

        ComboBox<PurchaseApiClient.Supplier> supplier = new ComboBox<>();
        supplier.setPromptText("Loading suppliers…");
        supplier.setMaxWidth(Double.MAX_VALUE);

        DatePicker purchaseDate = new DatePicker(LocalDate.now());
        purchaseDate.setEditable(false);
        purchaseDate.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(purchaseDate, Priority.ALWAYS);

        Button todayBtn = button("Today", "purchase-chip-btn", () -> purchaseDate.setValue(LocalDate.now()));
        Button yesterdayBtn = button("Yesterday", "purchase-chip-btn", () -> purchaseDate.setValue(LocalDate.now().minusDays(1)));

        Runnable syncDateChips = () -> {
            LocalDate val = purchaseDate.getValue();
            todayBtn.getStyleClass().remove("purchase-chip-btn-active");
            yesterdayBtn.getStyleClass().remove("purchase-chip-btn-active");
            if (LocalDate.now().equals(val)) {
                todayBtn.getStyleClass().add("purchase-chip-btn-active");
            } else if (LocalDate.now().minusDays(1).equals(val)) {
                yesterdayBtn.getStyleClass().add("purchase-chip-btn-active");
            }
        };
        purchaseDate.valueProperty().addListener((obs, oldVal, newVal) -> syncDateChips.run());
        syncDateChips.run();

        HBox dateRow = bar(purchaseDate, todayBtn, yesterdayBtn);

        ComboBox<Currency> currency = new ComboBox<>();
        currency.setPromptText("Loading currencies…");
        currency.setMaxWidth(Double.MAX_VALUE);

        Label currencyHint = small("Foreign currency purchases lock the MAD exchange rate when receipt is confirmed.");
        currencyHint.setWrapText(true);

        VBox callout = new VBox(3,
                heading("How draft purchases work"),
                small("Drafts are saved automatically and do not increase stock levels until you confirm receipt of the goods."));
        callout.getStyleClass().add("purchase-callout-info");
        callout.getChildren().forEach(node -> {
            if (node instanceof Label lbl) lbl.setWrapText(true);
        });

        Button cancelBtn = button("Cancel", "purchase-secondary", this::showList);
        Button submit = button("Create draft & add items →", "purchase-primary", () ->
                write("Creating draft…", feedback,
                        () -> api.createDraft(supplier.getValue().id(), currency.getValue().code(),
                                purchaseDate.getValue()),
                        id -> loadPurchase(id, "Draft created. Add products below before confirming receipt.")));
        submit.disableProperty().bind(writing.or(currency.valueProperty().isNull())
                .or(purchaseDate.valueProperty().isNull()).or(supplier.valueProperty().isNull()));

        HBox footerActions = bar(cancelBtn, spacer(), submit);

        VBox form = new VBox(16,
                new VBox(4, stepEyebrow, cardTitle, cardSub),
                new Separator(),
                field("Supplier", supplier),
                field("Purchase date", dateRow),
                new VBox(5, field("Billing currency", currency), currencyHint),
                callout,
                footerActions);
        form.getStyleClass().add("purchase-setup-card");
        form.setMaxWidth(560);
        form.setPrefWidth(560);

        VBox centerStage = new VBox(form);
        centerStage.setAlignment(Pos.CENTER);
        centerStage.setFillWidth(false);
        VBox.setVgrow(centerStage, Priority.ALWAYS);

        Button back = button("← Back to purchases", "purchase-secondary", this::showList);

        getChildren().setAll(
                pageHeading("New purchase", "Set up a new supplier order.", back),
                feedback,
                centerStage);

        api.suppliers().thenCombine(api.currencies(), DraftOptions::new).whenComplete((options, error) -> Platform.runLater(() -> {
            if (page != screen) return;
            if (error != null) {
                supplier.setPromptText("Supplier list unavailable");
                currency.setPromptText("Currency list unavailable");
                showFeedback(feedback, message(error), true);
                footerActions.getChildren().add(1, button("Retry", "purchase-secondary", this::showNewPurchase));
            } else {
                supplier.getItems().setAll(options.suppliers().stream()
                        .filter(row -> "ACTIVE".equals(row.status())).toList());
                supplier.setPromptText(supplier.getItems().isEmpty() ? "No active suppliers" : "Choose a supplier");
                currency.getItems().setAll(options.currencies());
                currency.setPromptText("Choose a currency");
                options.currencies().stream().filter(c -> "MAD".equals(c.code())).findFirst()
                        .ifPresent(currency::setValue);
                if (supplier.getItems().isEmpty()) {
                    showFeedback(feedback, "An active supplier is needed to create a purchase.", true);
                } else if (options.currencies().isEmpty()) {
                    showFeedback(feedback, "No currencies are configured.", true);
                }
            }
        }));
    }

    private void loadPurchase(long id) {
        loadPurchase(id, "");
    }

    private void loadPurchase(long id, String notice) {
        long page = ++screen;
        Button back = button("← Back to purchases", "purchase-secondary", this::showList);
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

        Button back = button("← Back to purchases", "purchase-secondary", this::showList);
        Button refresh = button("Refresh", "purchase-secondary", () -> loadPurchase(purchase.id()));
        refresh.disableProperty().bind(writing);

        VBox summary = card(
                bar(heading("Purchase overview"), spacer(), badge(status)),
                lifecycleStepper(status));

        HBox figures = bar(
                metric("Supplier", fallback(purchase.supplierName())),
                metric("Purchase date", date(purchase.purchaseDate())),
                metric("Currency", purchase.currencyCode()),
                metric("Order total", money(purchase.totalCurrency(), purchase.currencyCode())),
                metric("MAD equivalent", purchase.totalMad() == null ? "Pending confirmation" : money(purchase.totalMad(), "MAD")));
        summary.getChildren().add(figures);

        if (!"MAD".equals(purchase.currencyCode()) && purchase.rateToMadSnapshot() != null) {
            String rateText = "Exchange rate: 1 " + purchase.currencyCode() + " = "
                    + purchase.rateToMadSnapshot().stripTrailingZeros().toPlainString() + " MAD";
            if (purchase.rateSourceSnapshot() != null && !purchase.rateSourceSnapshot().isBlank()) {
                rateText += "  ·  Source: " + purchase.rateSourceSnapshot();
            }
            if (purchase.rateFromCacheSnapshot()) {
                rateText += " (cached)";
            }
            summary.getChildren().add(small(rateText));
        } else if (purchase.rateFromCacheSnapshot()) {
            summary.getChildren().add(small("Confirmed using a cached exchange rate."));
        }

        if (purchase.cancelledAt() != null) {
            summary.getChildren().add(small("Cancelled on " + DATE_TIME.format(purchase.cancelledAt())));
        } else if (purchase.confirmedAt() != null) {
            summary.getChildren().add(small("Receipt confirmed on " + DATE_TIME.format(purchase.confirmedAt())));
        }

        TableView<Line> lines = table();
        lines.getItems().setAll(purchase.items());
        lines.setPlaceholder(new Label(status == Status.DRAFT
                ? "No items in this draft yet. Click \"+ Add product\" to begin."
                : "No line items recorded."));

        addTagColumn(lines, "SKU", 130, line -> fallback(line.sku()), "purchase-sku-cell-tag");
        addTypedColumn(lines, "Product", 220, false,
                line -> line.productName() == null ? "Product #" + line.productId() : line.productName(),
                Function.identity(), String.CASE_INSENSITIVE_ORDER, "purchase-cell-strong");
        addTypedColumn(lines, "Qty", 80, true, Line::quantity, String::valueOf, Comparator.naturalOrder());
        addTypedColumn(lines, "Unit cost", 130, true, Line::unitPriceCurrency, PurchasesView::amount, Comparator.naturalOrder());
        addTypedColumn(lines, "Line total", 140, true, Line::lineTotalCurrency, PurchasesView::amount,
                Comparator.naturalOrder(), "purchase-cell-amount");
        addColumn(lines, "Serial numbers", 190, false, line ->
                line.serialNumbers() == null || line.serialNumbers().isEmpty()
                        ? (line.tracksSerials() && status == Status.DRAFT ? "Enter at confirmation" : "—")
                        : String.join(", ", line.serialNumbers()));

        int totalUnits = purchase.items().stream().mapToInt(Line::quantity).sum();
        HBox itemsFooter = bar(
                small(purchase.items().size() + " line(s)  ·  " + totalUnits + " total unit(s)"),
                spacer(),
                heading("Subtotal:  " + money(purchase.totalCurrency(), purchase.currencyCode())));
        itemsFooter.getStyleClass().add("purchase-items-footer");

        HBox lineHeader = bar(heading("Line items"), spacer());
        HBox actions = bar();

        if (status == Status.DRAFT) {
            Button remove = button("Remove selected", "purchase-secondary", () -> {
                Line selected = lines.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    write("Removing product…", feedback,
                            () -> api.removeLine(purchase.id(), selected.id()),
                            ignored -> loadPurchase(purchase.id(), "Product removed from the draft."));
                }
            });
            Button add = button("+ Add product", "purchase-secondary", () ->
                    dialogs.addLine(getScene().getWindow(), purchase).ifPresent(line ->
                            write("Adding product…", feedback,
                                    () -> api.addLine(purchase.id(), line.productId(), line.quantity(), line.unitPrice()),
                                    ignored -> loadPurchase(purchase.id(), "Product added to the draft."))));
            Button confirm = button("Confirm purchase", "purchase-primary", () ->
                    dialogs.confirm(getScene().getWindow(), purchase).ifPresent(serials ->
                            write("Confirming purchase…", feedback,
                                    () -> api.confirm(purchase.id(), serials),
                                    ignored -> loadPurchase(purchase.id(), "Purchase confirmed. Received stock has been added."))));

            remove.disableProperty().bind(writing.or(lines.getSelectionModel().selectedItemProperty().isNull()));
            add.disableProperty().bind(writing);
            confirm.disableProperty().bind(writing.or(new SimpleBooleanProperty(purchase.items().isEmpty())));

            lineHeader.getChildren().addAll(remove, add);
            actions.getChildren().addAll(
                    small("Confirm receipt only once goods arrive and quantities & costs match the invoice."),
                    spacer(),
                    confirm);
        } else if (status == Status.CONFIRMED) {
            Button cancel = button("Cancel purchase", "purchase-danger", () -> {
                if (approve("CANCEL RECEIPT",
                        "Cancel purchase #" + purchase.id() + "?",
                        "This reverses the stock receipt and deducts the received items from inventory, if stock levels still permit it.",
                        "Cancel purchase")) {
                    write("Cancelling purchase…", feedback,
                            () -> api.cancel(purchase.id()),
                            ignored -> loadPurchase(purchase.id(), "Purchase cancelled. The stock receipt was reversed."));
                }
            });
            cancel.disableProperty().bind(writing);
            actions.getChildren().addAll(
                    small("Received inventory has been added to stock."),
                    spacer(),
                    cancel);
        }

        VBox itemsCard = card(lineHeader, lines, itemsFooter);
        if (!actions.getChildren().isEmpty()) {
            itemsCard.getChildren().add(actions);
        }
        VBox.setVgrow(lines, Priority.ALWAYS);
        VBox.setVgrow(itemsCard, Priority.ALWAYS);

        getChildren().setAll(
                pageHeading("Purchase #" + purchase.id(), "Review supplier lines, totals, and receipt status.", back, refresh),
                feedback,
                summary,
                itemsCard);
    }

    private static HBox lifecycleStepper(Status status) {
        HBox stepper = new HBox(8);
        stepper.getStyleClass().add("purchase-stepper");
        stepper.setAlignment(Pos.CENTER_LEFT);

        if (status == Status.CANCELLED) {
            stepper.getChildren().addAll(
                    stepPill("1. Draft", "purchase-step-done"),
                    stepArrow(),
                    stepPill("2. Confirmed", "purchase-step-done"),
                    stepArrow(),
                    stepPill("3. Cancelled (Receipt Reversed)", "purchase-step-cancelled"));
            return stepper;
        }

        String s1 = status == Status.DRAFT ? "purchase-step-current" : "purchase-step-done";
        String s2 = status == Status.CONFIRMED ? "purchase-step-done" : "purchase-step-pending";

        stepper.getChildren().addAll(
                stepPill("1. Draft", s1),
                stepArrow(),
                stepPill("2. Confirmed & Stock Added", s2));
        return stepper;
    }

    private static Label stepPill(String text, String stateClass) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("purchase-step", stateClass);
        return pill;
    }

    private static Label stepArrow() {
        Label arrow = new Label("→");
        arrow.getStyleClass().add("purchase-muted");
        return arrow;
    }

    private static VBox kpiCard(String title, Label valueLabel, String hint, Runnable onClick) {
        Label head = small(title);
        valueLabel.getStyleClass().add("purchase-kpi-value");
        Label sub = small(hint);

        VBox card = new VBox(4, head, valueLabel, sub);
        card.getStyleClass().add("purchase-kpi-card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setOnMouseClicked(e -> onClick.run());
        return card;
    }

    private static void setKpiActive(VBox card, boolean active) {
        card.getStyleClass().remove("purchase-kpi-card-active");
        if (active) {
            card.getStyleClass().add("purchase-kpi-card-active");
        }
    }

    private boolean approve(String eyebrow, String title, String detail, String action) {
        ButtonType accept = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("ElectroStock · Purchases");

        var pane = dialog.getDialogPane();
        applyStyles(pane);
        pane.getStyleClass().add("purchases-dialog");
        pane.setPrefWidth(480);

        Label tag = new Label(eyebrow);
        tag.getStyleClass().add("purchase-eyebrow-danger");

        Label head = new Label(title);
        head.getStyleClass().add("purchase-confirm-dialog-title");
        head.setWrapText(true);

        Label body = new Label(detail);
        body.getStyleClass().add("purchase-confirm-dialog-body");
        body.setWrapText(true);

        VBox callout = new VBox(body);
        callout.getStyleClass().add("purchase-callout-danger");

        VBox content = new VBox(12, new VBox(4, tag, head), callout);
        content.setPadding(new Insets(6, 0, 2, 0));
        pane.setContent(content);

        pane.getButtonTypes().setAll(ButtonType.CANCEL, accept);
        Node confirmBtn = pane.lookupButton(accept);
        if (confirmBtn != null) {
            confirmBtn.getStyleClass().add("purchase-danger-solid");
        }
        Node cancelBtn = pane.lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) {
            cancelBtn.getStyleClass().add("purchase-secondary");
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

    private static HBox badge(Status status) {
        String key = status == Status.ALL ? "draft" : status.css;
        Region dot = new Region();
        dot.getStyleClass().addAll("purchase-status-dot", "purchase-status-dot-" + key);
        dot.setMinSize(6, 6);
        dot.setPrefSize(6, 6);
        dot.setMaxSize(6, 6);

        Label text = new Label(status == Status.ALL ? "Unknown" : status.text);
        text.getStyleClass().addAll("purchase-status-text", "purchase-status-text-" + key);

        HBox pill = new HBox(6, dot, text);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.getStyleClass().addAll("purchase-status", "purchase-status-" + key);
        return pill;
    }

    private static void addStatusColumn(TableView<Summary> table) {
        TableColumn<Summary, Status> column = new TableColumn<>("Status");
        column.setPrefWidth(165);
        column.setMinWidth(150);
        column.setComparator(Comparator.naturalOrder());
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(statusOf(cell.getValue().status())));
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
            column.getStyleClass().add("purchase-col-numeric");
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
        table.getStyleClass().add("purchase-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(140);
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

    static VBox field(String title, Node control) {
        Label label = new Label(title);
        label.getStyleClass().add("purchase-field-label");
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