package studyplanner.gui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

// Helper methods for making UI elements
class Widgets {

    private Widgets() {
    }

    static Label label(String text, String... styles) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styles);
        // otherwise underscores in titles disappear
        label.setMnemonicParsing(false);
        if (label.getStyleClass().contains("badge")) {
            // don't cut off badges
            label.setMinWidth(Region.USE_PREF_SIZE);
        }
        return label;
    }

    static Label wrapped(String text, String... styles) {
        Label label = label(text, styles);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    static Button button(String text, String id, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setOnAction(event -> action.run());
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    static Region spacer() {
        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    static ScrollPane scrolling(VBox content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        return pane;
    }

    static String courseAndTitle(String courseCode, String title) {
        return courseCode.isEmpty() ? title : courseCode + "  ·  " + title;
    }
}
