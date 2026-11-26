package studyplanner.gui;

import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import studyplanner.Dates;

// "What should I work on next" tab
class NextTab extends VBox {
    private final MainView main;
    private final VBox cards = new VBox(10);

    NextTab(MainView main) {
        this.main = main;
        cards.getStyleClass().add("page");
        cards.setId("next-cards");
        ScrollPane scroll = Widgets.scrolling(cards);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(scroll);
    }

    void show(Models.Snapshot data) {
        cards.getChildren().clear();
        if (data.ranking().isEmpty()) {
            cards.getChildren().add(Widgets.wrapped(
                    "Nothing left to do. Add assignments on the Assignments tab.", "muted"));
            return;
        }
        for (Models.Ranked item : data.ranking()) {
            Models.Assignment a = item.assignment();
            HBox top = new HBox(10,
                    Widgets.label(item.rank() + ".", "card-title"),
                    Widgets.label(Widgets.courseAndTitle(a.courseCode(), a.title()),
                            "card-title"),
                    Widgets.spacer(),
                    Widgets.label(item.risk().replace('_', ' '),
                            "badge", "risk-" + item.risk()));
            top.setAlignment(Pos.CENTER_LEFT);
            HBox bottom = new HBox(8,
                    Widgets.label("Due " + Dates.when(a.dueAt(), main.zone)
                            + "   Score " + Forms.number(item.score()), "muted"),
                    Widgets.spacer(),
                    Widgets.button("Log time", "next-log-" + a.id(), () -> main.logTime(a)),
                    Widgets.button("Edit", "next-edit-" + a.id(), () -> main.edit(a)),
                    Widgets.button("Mark done", "next-done-" + a.id(),
                            () -> main.toggleDone(a)));
            bottom.setAlignment(Pos.CENTER_LEFT);
            VBox card = new VBox(6, top, Widgets.wrapped(item.reason()), bottom);
            card.getStyleClass().add("card");
            cards.getChildren().add(card);
        }
    }
}
