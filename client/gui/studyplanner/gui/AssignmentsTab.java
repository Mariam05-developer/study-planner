package studyplanner.gui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import studyplanner.Dates;

// Table of assignments with filters for status and course
class AssignmentsTab extends VBox {
    private static final Models.Course ALL_COURSES = new Models.Course(-1, "All courses", "");

    private final MainView main;
    private final ObservableList<Models.Assignment> rows = FXCollections.observableArrayList();
    private final TableView<Models.Assignment> table = new TableView<>();
    private final ComboBox<String> statusBox = new ComboBox<>();
    private final ComboBox<Models.Course> courseBox = new ComboBox<>();
    private Button doneButton;
    private boolean refreshingCourses = false;

    AssignmentsTab(MainView main) {
        this.main = main;
        getStyleClass().add("page");
        setSpacing(10);
        setupTable();
        getChildren().addAll(createToolbar(), table);
    }

    private HBox createToolbar() {
        Button add = Widgets.button("Add assignment", "add",
                () -> Dialogs.assignment(main, null));
        add.getStyleClass().add("primary");

        Button edit = Widgets.button("Edit", "edit", () -> main.edit(selected()));
        Button log = Widgets.button("Log time", "log", () -> main.logTime(selected()));
        doneButton = Widgets.button("Mark done", "done", () -> main.toggleDone(selected()));
        Button delete = Widgets.button("Delete", "delete",
                () -> Dialogs.deleteAssignment(main, selected()));

        // these only work when a row is selected
        var selection = table.getSelectionModel().selectedItemProperty();
        edit.disableProperty().bind(selection.isNull());
        log.disableProperty().bind(selection.isNull());
        doneButton.disableProperty().bind(selection.isNull());
        delete.disableProperty().bind(selection.isNull());
        selection.addListener((obs, oldItem, newItem) -> {
            boolean completed = newItem != null && newItem.completed();
            doneButton.setText(completed ? "Reopen" : "Mark done");
        });

        statusBox.setId("filter-status");
        statusBox.getItems().addAll("Open", "Done", "All");
        statusBox.setValue("Open");
        statusBox.setOnAction(e -> applyFilters());

        courseBox.setId("filter-course");
        courseBox.getItems().add(ALL_COURSES);
        courseBox.setValue(ALL_COURSES);
        courseBox.setConverter(new StringConverter<Models.Course>() {
            @Override
            public String toString(Models.Course c) {
                return c == null ? "" : c.code();
            }

            @Override
            public Models.Course fromString(String s) {
                return null;
            }
        });
        courseBox.setOnAction(e -> {
            if (!refreshingCourses) {
                applyFilters();
            }
        });

        HBox toolbar = new HBox(8, add, edit, log, doneButton, delete, Widgets.spacer(),
                statusBox, courseBox);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    private void setupTable() {
        table.setId("table");

        TableColumn<Models.Assignment, Long> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        idColumn.setPrefWidth(55);

        TableColumn<Models.Assignment, String> courseColumn = new TableColumn<>("Course");
        courseColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().courseCode()));
        courseColumn.setPrefWidth(90);

        TableColumn<Models.Assignment, String> titleColumn = new TableColumn<>("Title");
        titleColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().title()));
        titleColumn.setPrefWidth(320);

        // the value is the ISO date so sorting is correct, but we show it nicely
        TableColumn<Models.Assignment, String> dueColumn = new TableColumn<>("Due");
        dueColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().dueAt()));
        dueColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String due, boolean empty) {
                super.updateItem(due, empty);
                setText(empty || due == null ? null : Dates.when(due, main.zone));
            }
        });
        dueColumn.setPrefWidth(200);

        TableColumn<Models.Assignment, Double> leftColumn = hoursColumn("Left");
        leftColumn.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().remainingHours()));

        TableColumn<Models.Assignment, Double> spentColumn = hoursColumn("Spent");
        spentColumn.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().hoursSpent()));

        TableColumn<Models.Assignment, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().completed() ? "done" : "open"));
        statusColumn.setPrefWidth(70);

        table.getColumns().add(idColumn);
        table.getColumns().add(courseColumn);
        table.getColumns().add(titleColumn);
        table.getColumns().add(dueColumn);
        table.getColumns().add(leftColumn);
        table.getColumns().add(spentColumn);
        table.getColumns().add(statusColumn);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Widgets.label("No assignments to show.", "muted"));

        SortedList<Models.Assignment> sorted = new SortedList<>(rows);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        // double click to edit
        table.setRowFactory(view -> {
            TableRow<Models.Assignment> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    main.edit(row.getItem());
                }
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private TableColumn<Models.Assignment, Double> hoursColumn(String name) {
        TableColumn<Models.Assignment, Double> column = new TableColumn<>(name);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double hours, boolean empty) {
                super.updateItem(hours, empty);
                setText(empty || hours == null ? null : Forms.hours(hours));
            }
        });
        column.setPrefWidth(70);
        return column;
    }

    private Models.Assignment selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    void show(Models.Snapshot data) {
        // refill the course dropdown but keep the same choice
        refreshingCourses = true;
        Models.Course previous = courseBox.getValue();
        courseBox.getItems().setAll(ALL_COURSES);
        courseBox.getItems().addAll(data.courses());
        courseBox.setValue(ALL_COURSES);
        for (Models.Course c : data.courses()) {
            if (previous != null && c.id() == previous.id()) {
                courseBox.setValue(c);
            }
        }
        refreshingCourses = false;
        applyFilters();
    }

    private void applyFilters() {
        if (main.data == null) {
            return;
        }
        Models.Assignment previous = selected();
        String status = statusBox.getValue();
        Models.Course course = courseBox.getValue();

        table.getSelectionModel().clearSelection();
        rows.clear();
        for (Models.Assignment a : main.data.assignments()) {
            if (status.equals("Open") && a.completed()) {
                continue;
            }
            if (status.equals("Done") && !a.completed()) {
                continue;
            }
            if (course != null && course.id() != ALL_COURSES.id()
                    && (a.courseId() == null || a.courseId() != course.id())) {
                continue;
            }
            rows.add(a);
        }

        // reselect the row that was selected before
        if (previous != null) {
            for (Models.Assignment a : table.getItems()) {
                if (a.id() == previous.id()) {
                    table.getSelectionModel().select(a);
                    break;
                }
            }
        }
    }
}
