package com.electrostock.frontend.view;

import com.electrostock.frontend.api.ProductApiClient;
import com.electrostock.frontend.api.ProductApiClient.AdjustmentReason;
import com.electrostock.frontend.api.ProductApiClient.Product;
import com.electrostock.frontend.api.ProductApiClient.StockAdjustmentRequest;
import com.electrostock.frontend.api.ProductApiClient.TrackedStockAdditionRequest;
import com.electrostock.frontend.api.ProductApiClient.TrackedStockRecoveryRequest;
import com.electrostock.frontend.api.ProductApiClient.TrackedStockRemovalRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

/** Adjustment form for a selected product. The backend remains the source of truth. */
final class StockDialogs {
    private StockDialogs() {}

    static boolean adjust(Window owner, Product product, long onHand, ProductApiClient api) {
        return new AdjustmentForm(owner, product, onHand, api).show();
    }

    private enum Mode {
        REMOVE("Remove"), ADD("Add"), RECOVER("Recover lost unit");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum Reason {
        DAMAGED("Damaged", AdjustmentReason.DAMAGED),
        LOST("Lost", AdjustmentReason.MISSING),
        COUNTING("Counting correction", AdjustmentReason.COUNTING_CORRECTION),
        OTHER("Other", AdjustmentReason.OTHER);

        private final String label;
        private final AdjustmentReason code;

        Reason(String label, AdjustmentReason code) {
            this.label = label;
            this.code = code;
        }

        @Override public String toString() { return label; }
    }

    private enum SerialState {
        EMPTY("Enter a serial number to check its status."),
        CHECKING("Checking serial…"),
        NOT_FOUND("Not registered for this product"),
        IN_STOCK("In stock"),
        SOLD("Sold"),
        UNDER_REPAIR("Under repair"),
        LOST("Lost · this unit can be recovered"),
        VOIDED("Voided · this unit cannot be recovered"),
        UNKNOWN("Unknown serial status"),
        FAILED("Couldn’t check this serial. Use Check again.");

        private final String label;
        SerialState(String label) { this.label = label; }

        static SerialState fromApi(String value) {
            if (value == null) return UNKNOWN;
            return switch (value) {
                case "IN_STOCK" -> IN_STOCK;
                case "SOLD" -> SOLD;
                case "UNDER_REPAIR" -> UNDER_REPAIR;
                case "LOST" -> LOST;
                case "VOIDED" -> VOIDED;
                default -> UNKNOWN;
            };
        }
    }

    private static final class AdjustmentForm {
        private final ProductApiClient api;
        private final Product product;
        private final int removalLimit;
        private final boolean tracked;
        private final Dialog<Boolean> dialog = new Dialog<>();
        private final ComboBox<Mode> mode = new ComboBox<>();
        private final ComboBox<Reason> reason = new ComboBox<>();
        private final TextField serial = new TextField();
        private final TextField quantity = new TextField();
        private final TextField cost = new TextField();
        private final TextArea explanation = new TextArea();
        private final Button checkAgain = new Button("Check again");
        private final Label serialStatus = label("", "stock-dialog-status");
        private final Label quantityHint = label("", "stock-dialog-muted");
        private final Label explanationLabel = label("Explanation (optional)", "stock-dialog-field");
        private final Label consequence = label("", "stock-dialog-warning");
        private final Label guidance = label("", "stock-dialog-muted");
        private final Label feedback = label("", "stock-dialog-error");
        private final VBox serialRow;
        private final VBox quantityRow;
        private final VBox costRow;
        private final VBox reasonRow;
        private final VBox form;
        private final Button save;
        private final Button cancel;
        private final PauseTransition debounce = new PauseTransition(Duration.millis(300));
        private SerialState state = SerialState.EMPTY;
        private long lookupVersion;
        private boolean syncingModes;
        private boolean busy;
        private boolean closed;
        private boolean uncertain;

        AdjustmentForm(Window owner, Product product, long onHand, ProductApiClient api) {
            this.api = api;
            this.product = product;
            this.tracked = product.tracksSerials();
            this.removalLimit = (int) Math.max(0, Math.min(Integer.MAX_VALUE, onHand));

            if (owner != null) dialog.initOwner(owner);
            dialog.setTitle("Adjust stock");
            dialog.setResizable(true);
            var pane = dialog.getDialogPane();
            pane.getStyleClass().add("stock-adjustment-dialog");
            pane.setPrefWidth(450);
            var css = StockDialogs.class.getResource("stock.css");
            if (css != null) pane.getStylesheets().add(css.toExternalForm());

            mode.setId("stock-adjust-mode");
            reason.setId("stock-adjust-reason");
            serial.setId("stock-adjust-serial");
            quantity.setId("stock-adjust-quantity");
            cost.setId("stock-adjust-cost");
            explanation.setId("stock-adjust-explanation");
            serialStatus.setId("stock-adjust-status");
            feedback.setId("stock-adjust-feedback");
            consequence.setId("stock-adjust-warning");

            mode.getItems().setAll(Mode.REMOVE, Mode.ADD);
            mode.setValue(!tracked && removalLimit == 0 ? Mode.ADD : Mode.REMOVE);
            mode.setMaxWidth(Double.MAX_VALUE);
            reason.setMaxWidth(Double.MAX_VALUE);
            reason.setPromptText("Choose a reason");
            serial.setPromptText("Enter or scan a serial number");
            quantity.setPromptText("Whole units");
            cost.setPromptText("e.g. 125.00");
            explanation.setPromptText("Add a note for the movement history");
            explanation.setWrapText(true);
            explanation.setPrefRowCount(2);

            serial.setTextFormatter(new TextFormatter<>(change ->
                    change.getControlNewText().length() <= 150 ? change : null));
            quantity.setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                if (next.isEmpty()) return change;
                if (!next.matches("[0-9]{1,10}")) return null;
                long value = Long.parseLong(next);
                long max = mode.getValue() == Mode.REMOVE ? removalLimit : Integer.MAX_VALUE;
                return value <= max ? change : null;
            }));

            checkAgain.getStyleClass().add("stock-dialog-recheck");
            checkAgain.setOnAction(e -> queueLookup(false));
            HBox statusLine = new HBox(8, serialStatus, checkAgain);
            statusLine.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(serialStatus, Priority.ALWAYS);
            serialStatus.setMaxWidth(Double.MAX_VALUE);
            serialRow = field("Serial number", serial, statusLine);
            quantityRow = field("Quantity", quantity, quantityHint);
            costRow = field("Unit cost (MAD)", cost);
            reasonRow = field("Reason", reason);
            explanationLabel.setLabelFor(explanation);
            VBox notes = new VBox(4, explanationLabel, explanation);

            VBox title = new VBox(4, label(product.name(), "stock-dialog-title"),
                    label(product.sku() + " · " + onHand + " on hand · "
                            + (tracked ? "Serial-tracked" : "Not serial-tracked"), "stock-dialog-muted"));
            form = new VBox(10, title, serialRow, field("Action", mode), quantityRow, costRow,
                    reasonRow, consequence, notes, guidance);
            pane.setContent(new VBox(9, form, feedback));

            ButtonType apply = new ButtonType("Apply adjustment", ButtonBar.ButtonData.OK_DONE);
            pane.getButtonTypes().addAll(ButtonType.CANCEL, apply);
            save = (Button) pane.lookupButton(apply);
            cancel = (Button) pane.lookupButton(ButtonType.CANCEL);
            save.setId("stock-adjust-save");
            save.getStyleClass().add("stock-dialog-apply");
            save.addEventFilter(ActionEvent.ACTION, e -> {
                e.consume();
                submit();
            });
            dialog.setResultConverter(button -> false);
            dialog.setOnCloseRequest(e -> { if (busy) e.consume(); });
            dialog.setOnHidden(e -> {
                closed = true;
                lookupVersion++;
                debounce.stop();
            });

            debounce.setOnFinished(e -> performLookup(lookupVersion, serial.getText().strip()));
            serial.textProperty().addListener((obs, before, after) -> queueLookup(true));
            mode.valueProperty().addListener((obs, before, after) -> {
                if (syncingModes || after == null) return;
                if (!tracked && after == Mode.REMOVE && quantityValue() > removalLimit) quantity.clear();
                updateReasons();
                updateForm();
            });
            reason.valueProperty().addListener((obs, before, after) -> updateForm());
            quantity.textProperty().addListener((obs, before, after) -> updateForm());
            cost.textProperty().addListener((obs, before, after) -> updateForm());
            explanation.textProperty().addListener((obs, before, after) -> updateForm());

            setFeedback("");
            updateReasons();
            updateForm();
        }

        boolean show() {
            return dialog.showAndWait().orElse(false);
        }

        private void queueLookup(boolean delayed) {
            debounce.stop();
            long revision = ++lookupVersion;
            if (serial.getText().isBlank()) {
                updateSerialState(SerialState.EMPTY);
                return;
            }
            updateSerialState(SerialState.CHECKING);
            if (delayed) debounce.playFromStart();
            else performLookup(revision, serial.getText().strip());
        }

        private void performLookup(long revision, String value) {
            api.unitStatus(product.id(), value).whenComplete((status, error) -> Platform.runLater(() -> {
                if (closed || revision != lookupVersion) return;
                updateSerialState(error != null ? SerialState.FAILED
                        : status.map(result -> SerialState.fromApi(result.status())).orElse(SerialState.NOT_FOUND));
            }));
        }

        private void updateSerialState(SerialState newState) {
            state = newState;
            List<Mode> available = state == SerialState.LOST ? List.of(Mode.REMOVE, Mode.ADD, Mode.RECOVER)
                    : List.of(Mode.REMOVE, Mode.ADD);
            if (!mode.getItems().equals(available)) {
                Mode previous = mode.getValue();
                syncingModes = true;
                try {
                    mode.getItems().setAll(available);
                    mode.setValue(available.contains(previous) ? previous : Mode.REMOVE);
                } finally {
                    syncingModes = false;
                }
                updateReasons();
            }
            updateForm();
        }

        private void updateReasons() {
            Mode action = mode.getValue();
            Reason previous = reason.getValue();
            List<Reason> available = action == Mode.REMOVE
                    ? List.of(Reason.DAMAGED, Reason.LOST, Reason.COUNTING, Reason.OTHER)
                    : action == Mode.ADD ? List.of(Reason.COUNTING, Reason.OTHER) : List.of();
            reason.getItems().setAll(available);
            reason.setValue(previous != null && available.contains(previous) ? previous : null);
        }

        private void updateForm() {
            Mode action = mode.getValue();
            Reason why = reason.getValue();
            visible(serialRow, tracked);
            visible(quantityRow, !tracked);
            visible(costRow, action == Mode.ADD);
            visible(reasonRow, action != Mode.RECOVER);
            serialStatus.setText(state.label);
            checkAgain.setDisable(serial.getText().isBlank() || state == SerialState.CHECKING);
            explanationLabel.setText(why == Reason.OTHER ? "Explanation (required)" : "Explanation (optional)");
            quantityHint.setText(action == Mode.REMOVE
                    ? removalLimit > 0 ? "Available to remove: " + removalLimit : "No stock on hand to remove."
                    : "Enter the number of units to add.");

            String warning = "";
            if (tracked && action == Mode.REMOVE && why != null) {
                warning = why == Reason.LOST
                        ? "This unit will be marked lost and can be recovered later."
                        : "This unit will be permanently voided and cannot be recovered.";
            }
            if (tracked && action == Mode.RECOVER) {
                warning = "This unit will return to stock as a counting correction.";
            }
            consequence.setText(warning);
            visible(consequence, !warning.isEmpty());

            String problem = validationMessage();
            guidance.setText(problem);
            visible(guidance, !problem.isEmpty());
            save.setDisable(busy || uncertain || !problem.isEmpty());
        }

        private String validationMessage() {
            Mode action = mode.getValue();
            Reason why = reason.getValue();
            if (action == null) return "Choose an action.";
            if (tracked) {
                if (serial.getText().isBlank()) return "A serial number is required.";
                if (state == SerialState.CHECKING) return "Wait for the serial check to finish.";
                if (state == SerialState.FAILED || state == SerialState.UNKNOWN) return "Verify this serial before saving.";
                if (action == Mode.REMOVE && state != SerialState.IN_STOCK) {
                    return "Only units currently in stock can be removed.";
                }
                if (action == Mode.ADD && state != SerialState.NOT_FOUND) {
                    return "Add requires a new serial number. Use Recover for a lost unit.";
                }
                if (action == Mode.RECOVER && state != SerialState.LOST) return "Only lost units can be recovered.";
            } else {
                int units = quantityValue();
                if (action == Mode.REMOVE && removalLimit == 0) return "No stock is available to remove.";
                if (units <= 0) return "Enter a positive whole quantity.";
                if (action == Mode.REMOVE && units > removalLimit) return "The quantity exceeds the stock on hand.";
            }
            if (action == Mode.ADD && costValue() == null) {
                return "Enter a nonnegative unit cost with up to four decimal places.";
            }
            if (action != Mode.RECOVER && why == null) return "Choose a reason for the adjustment.";
            if (why == Reason.OTHER && explanation.getText().isBlank()) return "An explanation is required for Other.";
            return "";
        }

        private int quantityValue() {
            try { return Integer.parseInt(quantity.getText()); }
            catch (NumberFormatException invalid) { return 0; }
        }

        private BigDecimal costValue() {
            String amount = cost.getText().strip().replace(',', '.');
            return amount.matches("[0-9]{1,15}(?:\\.[0-9]{1,4})?") ? new BigDecimal(amount) : null;
        }

        private void submit() {
            if (busy || uncertain || !validationMessage().isEmpty()) return;
            Mode action = mode.getValue();
            Reason why = reason.getValue();
            String note = explanation.getText().strip();
            String serialNumber = serial.getText().strip();
            BigDecimal unitCost = action == Mode.ADD ? costValue() : null;

            busy = true;
            form.setDisable(true);
            cancel.setDisable(true);
            setFeedback("");
            updateForm();
            save.setText("Saving…");

            CompletableFuture<Void> request;
            if (!tracked) {
                int change = (action == Mode.REMOVE ? -1 : 1) * quantityValue();
                request = api.adjustStock(product.id(), new StockAdjustmentRequest(change, unitCost, why.code, note));
            } else {
                request = switch (action) {
                    case REMOVE -> api.removeTrackedUnit(product.id(),
                            new TrackedStockRemovalRequest(serialNumber, why.code, note));
                    case ADD -> api.addTrackedUnit(product.id(),
                            new TrackedStockAdditionRequest(serialNumber, unitCost, why.code, note));
                    case RECOVER -> api.recoverTrackedUnit(product.id(),
                            new TrackedStockRecoveryRequest(serialNumber, note));
                };
            }

            request.whenComplete((unused, error) -> Platform.runLater(() -> {
                if (closed) return;
                busy = false;
                if (error == null) {
                    dialog.setResult(true);
                    dialog.close();
                    return;
                }
                form.setDisable(false);
                cancel.setDisable(false);
                save.setText("Apply adjustment");

                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                String detail = cause.getMessage();
                if (cause instanceof IllegalStateException && detail != null && detail.startsWith("HTTP ")) {
                    setFeedback(detail);
                } else {
                    uncertain = true;
                    setFeedback("Couldn’t confirm whether the adjustment was saved. Close this dialog and refresh Stock before trying again.");
                }
                updateForm();
            }));
        }

        private void setFeedback(String text) {
            feedback.setText(text);
            visible(feedback, !text.isEmpty());
        }
    }

    private static VBox field(String name, Node input, Node... extras) {
        Label label = label(name, "stock-dialog-field");
        label.setLabelFor(input);
        VBox row = new VBox(4, label, input);
        row.getChildren().addAll(extras);
        return row;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }

    private static void visible(Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }
}
