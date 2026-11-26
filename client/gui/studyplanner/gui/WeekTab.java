package studyplanner.gui;

import java.time.LocalDate;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import studyplanner.Dates;

// Weekly view, one column per day
class WeekTab extends VBox {
    private final MainView main;
    private final Label heading = Widgets.label("", "card-title");
    private final GridPane grid = new GridPane();

    WeekTab(MainView main) {
        this.main = main;
        getStyleClass().add("page");
        setSpacing(10);
        heading.setId("week-label");
        grid.setId("week-grid");
        grid.setHgap(6);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            grid.getColumnConstraints().add(column);
        }

        HBox toolbar = new HBox(8,
                Widgets.button("< Previous", "week-prev", () -> move(-1)),
                Widgets.button("This week", "week-today", () -> {
                    main.weekOf = null;
                    main.refresh();
                }),
                Widgets.button("Next >", "week-next", () -> move(1)),
                heading);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(toolbar, scroll);
    }

    private void move(int weeks) {
        if (main.data == null) {
            return;
        }
        // use weekOf so clicking quickly still works
        LocalDate from = main.weekOf != null
                ? main.weekOf : LocalDate.parse(main.data.week().start());
        main.weekOf = from.plusWeeks(weeks);
        main.refresh();
    }

    void show(Models.Snapshot data) {
        Models.Week week = data.week();
        heading.setText(Dates.day(week.start()) + " to " + Dates.day(week.end()) + "   "
                + Forms.hours(week.plannedHours()) + " of "
                + Forms.hours(week.capacityHours()) + " planned");
        grid.getChildren().clear();
        for (int i = 0; i < week.days().size(); i++) {
            grid.add(column(week.days().get(i)), i, 0);
        }
    }

    private VBox column(Models.Day day) {
        ProgressBar bar = new ProgressBar(
                Math.min(1, day.plannedHours() / day.capacityHours()));
        bar.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(5,
                Widgets.label(Dates.day(day.date()), "card-title"),
                bar,
                Widgets.label(Forms.hours(day.plannedHours()) + " / "
                        + Forms.hours(day.capacityHours()), "muted"));
        box.getStyleClass().add("day");
        if (day.today()) {
            box.getStyleClass().add("today");
        }
        if (day.overloaded()) {
            box.getStyleClass().add("overloaded");
            box.getChildren().add(Widgets.label("Overloaded", "badge", "risk-overdue"));
        }
        if (!day.due().isEmpty()) {
            box.getChildren().add(Widgets.label("Due", "section"));
            for (Models.Due due : day.due()) {
                box.getChildren().add(Widgets.wrapped(
                        due.title() + " at " + Dates.clock(due.dueAt(), main.zone)
                                + (due.completed() ? " (done)" : ""),
                        "chip", due.completed() ? "chip-done" : "chip-due"));
            }
        }
        if (!day.plan().isEmpty()) {
            box.getChildren().add(Widgets.label("Work on", "section"));
            for (Models.Work work : day.plan()) {
                box.getChildren().add(Widgets.wrapped(
                        work.title() + ": " + Forms.hours(work.hours()), "chip"));
            }
        }
        return box;
    }
}
