package studyplanner.gui;

import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

// Reminders tab
class RemindersTab extends VBox {
    private final MainView main;
    private final VBox cards = new VBox(10);

    RemindersTab(MainView main) {
        this.main = main;
        cards.getStyleClass().add("page");
        ScrollPane scroll = Widgets.scrolling(cards);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    void show(Models.Snapshot data) {
        cards.getChildren().clear();
        if (data.reminders().isEmpty()) {
            cards.getChildren().add(Widgets.label("No reminders right now.", "muted"));
            return;
        }
        for (Models.Reminder reminder : data.reminders()) {
            Models.Assignment a = reminder.assignment();
            boolean overdue = reminder.kind().equals("overdue");
            var message = Widgets.wrapped(reminder.message());
            HBox.setHgrow(message, Priority.ALWAYS);
            message.setMaxWidth(Double.MAX_VALUE);
            HBox card = new HBox(10,
                    Widgets.label(overdue ? "overdue" : "due soon",
                            "badge", overdue ? "risk-overdue" : "risk-tight"),
                    message,
                    Widgets.button("Log time", "reminder-log-" + a.id(),
                            () -> main.logTime(a)),
                    Widgets.button("Mark done", "reminder-done-" + a.id(),
                            () -> main.toggleDone(a)),
                    Widgets.button("Dismiss", "dismiss-" + a.id(), () -> main.change(() -> {
                        main.app.service.dismissReminder(a.id());
                        return null;
                    })));
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("card");
            cards.getChildren().add(card);
        }
    }
}
