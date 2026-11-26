package studyplanner.gui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

// Main window with the tabs
class MainView extends BorderPane {
    private static final Duration AUTO_REFRESH = Duration.minutes(2);

    final GuiApp app;
    final ZoneId zone = ZoneId.systemDefault();
    LocalDate weekOf;
    Models.Snapshot data;

    private final Label userLabel = Widgets.label("", "muted");
    private final Label status = Widgets.label("", "muted");
    private final Label banner = Widgets.wrapped("");
    private final NextTab next = new NextTab(this);
    private final AssignmentsTab assignments = new AssignmentsTab(this);
    private final WeekTab week = new WeekTab(this);
    private final CoursesTab courses = new CoursesTab(this);
    private final RemindersTab reminders = new RemindersTab(this);
    private final Tab remindersTab = tab("Reminders", reminders);
    private final Timeline timer;
    private final Set<String> seenReminders = new HashSet<>();
    private int refreshes;

    MainView(GuiApp app) {
        this.app = app;

        Label title = Widgets.label("Study Planner", "heading");
        HBox header = new HBox(12, title, userLabel, Widgets.spacer(), status,
                Widgets.button("Refresh", "refresh", this::refresh),
                Widgets.button("Settings", "settings", () -> Dialogs.settings(this)),
                Widgets.button(app.service.isGuest() ? "Exit guest mode" : "Log out",
                        "logout", this::logOut));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");

        banner.setId("banner");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setOnMouseClicked(event -> Widgets.setShown(banner, false));
        Widgets.setShown(banner, false);
        setTop(new VBox(header, banner));

        TabPane tabs = new TabPane(tab("Next up", next), tab("Assignments", assignments),
                tab("Week", week), tab("Courses", courses), remindersTab);
        tabs.setId("tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setCenter(tabs);

        // auto refresh
        timer = new Timeline(new KeyFrame(AUTO_REFRESH, event -> {
            if (refreshes == 0) {
                refresh();
            }
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    private static Tab tab(String name, javafx.scene.Node content) {
        return new Tab(name, content);
    }

    void close() {
        timer.stop();
    }

    void refresh() {
        LocalDate wanted = weekOf;
        refreshes++;
        status.setText("Loading...");
        app.tasks.run(() -> app.service.load(wanted), snapshot -> {
            refreshes--;
            status.setText("Updated "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            show(snapshot);
        }, error -> {
            refreshes--;
            status.setText("Not updated");
            fail(error);
        });
    }

    private void show(Models.Snapshot snapshot) {
        data = snapshot;
        userLabel.setText(app.service.description(snapshot.user()));
        next.show(snapshot);
        assignments.show(snapshot);
        week.show(snapshot);
        courses.show(snapshot);
        reminders.show(snapshot);

        int count = snapshot.reminders().size();
        remindersTab.setText(count == 0 ? "Reminders" : "Reminders (" + count + ")");
        int fresh = 0;
        for (Models.Reminder reminder : snapshot.reminders()) {
            if (seenReminders.add(reminder.assignment().id() + reminder.kind()
                    + reminder.assignment().dueAt())) {
                fresh++;
            }
        }
        // show the banner for new reminders
        boolean showingReminders = banner.isVisible()
                && banner.getStyleClass().contains("banner-reminder");
        if (count > 0 && (fresh > 0 || showingReminders)) {
            notice("You have " + (count == 1 ? "1 reminder" : count + " reminders")
                    + ". See the Reminders tab. (Click to hide.)", "banner-reminder");
        } else {
            Widgets.setShown(banner, false);
        }
    }

    private void notice(String text, String style) {
        banner.setText(text);
        banner.getStyleClass().removeAll("banner-reminder", "banner-error");
        banner.getStyleClass().add(style);
        Widgets.setShown(banner, true);
    }

    void fail(Throwable error) {
        if (!app.handleExpiredSession(error)) {
            notice(GuiApp.describe(error) + " (Click to hide.)", "banner-error");
        }
    }

    void change(Callable<?> work) {
        app.tasks.run(work, done -> refresh(), error -> {
            // reload first, then show the error
            if (!app.handleExpiredSession(error)) {
                refresh();
                app.tasks.run(() -> null, done -> fail(error), this::fail);
            }
        });
    }

    // actions offered on several tabs

    void logTime(Models.Assignment assignment) {
        Dialogs.logTime(this, assignment);
    }

    void toggleDone(Models.Assignment assignment) {
        change(() -> {
            app.service.setCompleted(assignment.id(), !assignment.completed());
            return null;
        });
    }

    void edit(Models.Assignment assignment) {
        Dialogs.assignment(this, assignment);
    }

    private void logOut() {
        setDisable(true);
        Planner leaving = app.service;
        app.tasks.run(() -> {
            leaving.signOut();
            return null;
        }, done -> app.showLogin(null), error -> app.showLogin(GuiApp.describe(error)));
    }
}
