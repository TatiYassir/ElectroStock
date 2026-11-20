package com.electrostock.frontend.view;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Catalogue thumbnails: every kind of product gets its own drawing and its own colour,
 * shown on a soft tinted tile. Unknown products get a package icon whose colour
 * is derived from the category, so a category always keeps the same colour.
 */
final class ProductIcons {
    private ProductIcons() {}

    private record Look(Pattern match, String path, String color, String tint) {
        Look(String regex, String path, String color, String tint) {
            this(Pattern.compile(regex), path, color, tint);
        }
    }

    // Order matters: the first match wins ("monitor arm" must be tested before "monitor").
    private static final List<Look> LOOKS = List.of(
        new Look("\\b(arms?|mounts?|stands?|brackets?)\\b",
                "M3 21H17 M9 21V16L17 8L13 5L5 13 M12 4L15 1L22 7L19 10Z", "#6d4aff", "#ece8ff"),
        new Look("monitor|display|screen|\\btv\\b",
                "M2 3H22V17H2Z M12 17V21 M6 21H18", "#0b7fd1", "#e1f1fc"),
        new Look("keyboard",
                "M1 6H23V18H1Z M4 10H6 M9 10H11 M14 10H16 M19 10H21 M6 14H18", "#e8590c", "#fff0e6"),
        new Look("mouse|trackball|trackpad",
                "M5 11C5 1 19 1 19 11V15C19 24 5 24 5 15Z M12 4V10", "#d6336c", "#fde7ef"),
        new Look("headphone|headset|earphone|earbud|airpod",
                "M4 15V12A8 8 0 0 1 20 12V15 M4 14H7V20H4Z M17 14H20V20H17Z", "#9c36b5", "#f5e6fa"),
        new Look("speaker|soundbar|audio",
                "M6 2H18V22H6Z M9 15A3 3 0 1 0 15 15A3 3 0 1 0 9 15 M12 6H12.01", "#f76707", "#fff0e0"),
        new Look("micro?phone|\\bmic\\b",
                "M9 2H15V12A3 3 0 0 1 9 12Z M5 11A7 7 0 0 0 19 11 M12 18V22 M8 22H16", "#e03131", "#ffe8e8"),
        new Look("web ?cam|camera|\\bcam\\b",
                "M2 7H7L9 4H15L17 7H22V20H2Z M8 13.5A4 4 0 1 0 16 13.5A4 4 0 1 0 8 13.5", "#c2255c", "#fde8f0"),
        new Look("laptop|notebook|macbook",
                "M4 3H20V15H4Z M4 15L1 20H23L20 15", "#3b5bdb", "#e6ebfd"),
        new Look("tablet|ipad",
                "M4 3H20V21H4Z M11 18H13", "#5f3dc4", "#eee8fb"),
        new Look("phone|smartphone|mobile",
                "M7 2H17V22H7Z M11 18H13", "#1098ad", "#dff6f9"),
        new Look("watch|band|wearable",
                "M8 2H16L17 7H7Z M8 22H16L17 17H7Z M7 7H17V17H7Z M12 10V12L14 13", "#364fc7", "#e5eafc"),
        new Look("printer|scanner|toner|ink",
                "M6 9V2H18V9 M6 18H4V9H20V18H18 M6 14H18V22H6Z", "#495057", "#eceff2"),
        new Look("router|modem|wifi|wi-fi|network|switch|ethernet|access point",
                "M3 15H21V21H3Z M7 18H7.01 M11 18H11.01 M12 15V10 M8 7A6 6 0 0 1 16 7 M5 4A10 10 0 0 1 19 4",
                "#1971c2", "#e3f0fc"),
        new Look("\\bhubs?\\b|dock|adapter|adaptor|dongle|splitter",
                "M2 11H22V21H2Z M6 16H9 M14 16H17 M11 11V7Q11 3 17 3H20", "#0a8f6a", "#ddf5ec"),
        new Look("cable|cord|wire|hdmi|usb-c|lightning",
                "M2 4H7V9H2Z M4 9V15Q4 21 10 21Q16 21 16 15V9 M13 4H20V9H13Z", "#e67700", "#fff3d9"),
        new Look("charger|power|plug|supply|socket|surge",
                "M9 2V7 M15 2V7 M6 7H18V12A6 6 0 0 1 6 12Z M12 18V22", "#f08c00", "#fff4da"),
        new Look("battery|powerbank|power bank|ups",
                "M2 7H19V17H2Z M22 10V14 M6 10V14 M10 10V14", "#2f9e44", "#e2f6e6"),
        new Look("ssd|hdd|drive|disk|storage|flash|memory card|\\bsd\\b|usb",
                "M3 5H21V19H3Z M3 14H21 M17 17H17.01 M7 17H7.01", "#0c8599", "#ddf4f8"),
        new Look("\\bram\\b|cpu|processor|gpu|graphics|chip|motherboard|component",
                "M6 6H18V18H6Z M9 9H15V15H9Z M9 2V6 M15 2V6 M9 18V22 M15 18V22 M2 9H6 M2 15H6 M18 9H22 M18 15H22",
                "#7048e8", "#ece7fd"),
        new Look("gamepad|controller|joystick|gaming|console",
                "M6 8H18A4 4 0 0 1 22 12V15A3 3 0 0 1 16 16L15 14H9L8 16A3 3 0 0 1 2 15V12A4 4 0 0 1 6 8Z"
                        + " M7 10.5V13.5 M5.5 12H8.5 M16 11.5H16.01 M18 12.5H18.01", "#ae3ec9", "#f7e8fb"),
        new Look("lamp|light|bulb|led",
                "M9 18H15 M10 21H14 M12 2A7 7 0 0 0 8 14V18H16V14A7 7 0 0 0 12 2Z", "#f59f00", "#fff6d9")
    );

    private static final String PACKAGE = "M12 2L21 7V17L12 22L3 17V7Z M3 7L12 12L21 7 M12 12V22";
    private static final String[][] FALLBACK = {
        {"#087e75", "#dcf4f1"}, {"#4263eb", "#e5eafd"}, {"#d9480f", "#ffece1"},
        {"#a61e4d", "#fbe6ee"}, {"#5c940d", "#eaf6d9"}, {"#7048e8", "#ece7fd"}
    };

    static StackPane forProduct(String name, String category) {
        return forProduct(name, category, 46);
    }

    /** @param size side of the rounded tile in pixels; the drawing fills about 56% of it. */
    static StackPane forProduct(String name, String category, double size) {
        String n = clean(name);
        String c = clean(category);
        Look look = find(n);
        if (look == null) look = find(c);

        String path, color, tint;
        if (look != null) {
            path = look.path(); color = look.color(); tint = look.tint();
        } else {
            String[] fallback = FALLBACK[Math.floorMod(c.hashCode(), FALLBACK.length)];
            path = PACKAGE; color = fallback[0]; tint = fallback[1];
        }

        return tile(path, color, tint, size);
    }

    /** Coloured rounded tile for the summary cards. kind: total | in | low | out. */
    static StackPane stat(String kind, double size) {
        return switch (kind) {
            case "in"  -> tile("M22 11.08V12A10 10 0 1 1 15.7 3 M22 4L12 14.01L9 11.01", "#16a34a", "#e0f7e8", size);
            case "low" -> tile("M12 3L22 20H2Z M12 10V14 M12 17H12.01", "#d97706", "#fff1d6", size);
            case "out" -> tile("M12 22A10 10 0 1 0 12 2A10 10 0 1 0 12 22 M4.9 4.9L19.1 19.1", "#dc2626", "#ffe4e4", size);
            default    -> tile(PACKAGE, "#0d9488", "#dcf5f1", size);
        };
    }

    /** Bare stroked icon without a tile. */
    static StackPane plain(String path, String color, double size) {
        double scale = size / 24.0;
        SVGPath drawing = new SVGPath();
        drawing.setContent(path);
        drawing.setFill(Color.TRANSPARENT);
        drawing.setStroke(Color.web(color));
        drawing.setStrokeWidth(2.0 / scale);
        drawing.setStrokeLineCap(StrokeLineCap.ROUND);
        drawing.setStrokeLineJoin(StrokeLineJoin.ROUND);
        drawing.setScaleX(scale);
        drawing.setScaleY(scale);
        StackPane box = new StackPane(drawing);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    private static StackPane tile(String path, String color, String tint, double size) {
        double scale = size * 0.52 / 24.0;
        SVGPath drawing = new SVGPath();
        drawing.setContent(path);
        drawing.setFill(Color.TRANSPARENT);
        drawing.setStroke(Color.web(color));
        drawing.setStrokeWidth(1.9 / scale);          // stays ~1.9px after scaling
        drawing.setStrokeLineCap(StrokeLineCap.ROUND);
        drawing.setStrokeLineJoin(StrokeLineJoin.ROUND);
        drawing.setScaleX(scale);
        drawing.setScaleY(scale);

        StackPane box = new StackPane(drawing);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        long radius = Math.round(size * 0.3);
        box.setStyle("-fx-background-color: linear-gradient(to bottom right, " + tint + ", " + rgba(color, 0.16) + ");"
                + "-fx-background-radius: " + radius + ";"
                + "-fx-border-color: " + rgba(color, 0.20) + ";"
                + "-fx-border-radius: " + radius + ";");
        return box;
    }

    private static Look find(String text) {
        if (text.isEmpty()) return null;
        for (Look look : LOOKS) if (look.match().matcher(text).find()) return look;
        return null;
    }

    private static String clean(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String rgba(String hex, double alpha) {
        Color c = Color.web(hex);
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255), alpha);
    }
}