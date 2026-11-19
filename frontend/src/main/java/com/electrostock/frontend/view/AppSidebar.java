package com.electrostock.frontend.view;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Left navigation: brand header, grouped icon buttons, active-page highlight, footer. */
public class AppSidebar extends VBox {
    private static final PseudoClass CURRENT_PAGE = PseudoClass.getPseudoClass("current-page");

    private static final String BOLT = "M13 2L4 14H11L10 22L20 9H13Z";
    private static final Map<String, String> ICONS = Map.of(
            "Dashboard", "M3 3H10V12H3Z M14 3H21V8H14Z M14 12H21V21H14Z M3 16H10V21H3Z",
            "Products",  "M12 2L21 7V17L12 22L3 17V7Z M3 7L12 12L21 7 M12 12V22",
            "Sales",     "M3 4H6L8 16H19L21 7H7 M10 20H10.01 M17 20H17.01",
            "Purchases", "M12 3V15 M7 10L12 15L17 10 M4 17V21H20V17",
            "Suppliers", "M1 6H14V17H1Z M14 10H19L23 14V17H14Z M4 19A2 2 0 1 0 8 19A2 2 0 1 0 4 19 M16 19A2 2 0 1 0 20 19A2 2 0 1 0 16 19",
            "Stock",     "M12 2L22 7L12 12L2 7Z M2 12L12 17L22 12 M2 17L12 22L22 17",
            "Reports",   "M3 3V21H21 M8 17V11 M13 17V6 M18 17V13");

    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Consumer<String> onSelect;

    public AppSidebar(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        getStyleClass().add("side-nav");
        setPrefWidth(236);
        setMinWidth(236);
        setMaxWidth(236);

        Label name = new Label("ElectroStock");
        name.getStyleClass().add("nav-brand-name");
        Label tagline = new Label("Inventory & sales");
        tagline.getStyleClass().add("nav-brand-tagline");
        StackPane logo = new StackPane(icon(BOLT, 1.0));
        logo.getStyleClass().add("nav-logo");
        HBox brand = new HBox(12, logo, new VBox(1, name, tagline));
        brand.getStyleClass().add("nav-brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(brand);

        section("Overview", "Dashboard");
        section("Trade", "Sales", "Purchases");
        section("Inventory", "Products", "Stock", "Suppliers");
        section("Insights", "Reports");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label version = new Label("ElectroStock  ·  v1.0");
        version.getStyleClass().add("nav-footer");
        getChildren().addAll(spacer, version);
    }

    /** Highlights the button of the page that is currently shown. */
    public void setCurrent(String page) {
        buttons.forEach((name, button) -> button.pseudoClassStateChanged(CURRENT_PAGE, name.equals(page)));
    }

    private void section(String title, String... pages) {
        Label label = new Label(title.toUpperCase());
        label.getStyleClass().add("nav-section");
        getChildren().add(label);
        for (String page : pages) {
            Button button = new Button(page, icon(ICONS.get(page), 0.84));
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setMnemonicParsing(false);
            button.setOnAction(e -> onSelect.accept(page));
            buttons.put(page, button);
            getChildren().add(button);
        }
    }

    private static StackPane icon(String path, double scale) {
        SVGPath drawing = new SVGPath();
        drawing.setContent(path);
        drawing.getStyleClass().add("nav-icon");
        drawing.setStrokeLineCap(StrokeLineCap.ROUND);
        drawing.setStrokeLineJoin(StrokeLineJoin.ROUND);
        drawing.setScaleX(scale);
        drawing.setScaleY(scale);
        StackPane box = new StackPane(drawing);
        box.setMinSize(22, 22);
        box.setPrefSize(22, 22);
        box.setMaxSize(22, 22);
        box.setMouseTransparent(true);
        return box;
    }
}