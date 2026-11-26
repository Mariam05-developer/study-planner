package studyplanner.gui;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

// All the popup windows
class Dialogs {
    private static final Models.Course NO_COURSE = new Models.Course(-1, "(none)", "");

    private Dialogs() {
    }

    static void assignment(MainView main, Models.Assignment original) {
        if (main.data == null) {
            return;
        }
        TextField title = field("f-title", "");
        ComboBox<Models.Course> course = new ComboBox<>();
        course.setId("f-course");
        course.getItems().add(NO_COURSE);
        course.getItems().addAll(main.data.courses());
        course.setValue(NO_COURSE);
        course.setConverter(new StringConverter<>() {
            @Override
            public String toString(Models.Course value) {
                if (value == null) {
                    return "";
                }
                return value.name().isEmpty()
                        ? value.code() : value.code() + " - " + value.name();
            }

            @Override
            public Models.Course fromString(String text) {
                return null;
            }
        });
        DatePicker date = datePicker();
        date.setValue(LocalDate.now(main.zone).plusDays(7));
        TextField time = field("f-time", "23:59");
        TextField hours = field("f-hours", "");
        hours.setPromptText("for example 4 or 1.5");
        TextField spent = field("f-spent", "0");
        TextField remind = field("f-remind", "");
        remind.setPromptText("blank: " + main.data.user().reminderHours() + " (your default)");
        TextArea notes = new TextArea();
        notes.setId("f-notes");
        notes.setPrefRowCount(3);
        notes.setPrefColumnCount(28);
        notes.setWrapText(true);

        if (original != null) {
            Forms.AssignmentInput start = Forms.inputFor(original, main.zone);
            title.setText(start.title());
            course.getItems().stream()
                    .filter(c -> Long.valueOf(c.id()).equals(start.courseId()))
                    .findFirst().ifPresent(course::setValue);
            date.setValue(start.date());
            time.setText(start.time());
            hours.setText(start.hours());
            spent.setText(start.spent());
            remind.setText(start.remind());
            notes.setText(start.notes());
        }

        GridPane grid = grid();
        int row = 0;
        add(grid, row++, "Title", title);
        add(grid, row++, "Course", course);
        add(grid, row++, "Due date", date);
        add(grid, row++, "Due time", time);
        add(grid, row++, "Estimated hours", hours);
        if (original != null) {
            add(grid, row++, "Hours spent", spent);
        }
        add(grid, row++, "Remind (hours before)", remind);
        add(grid, row, "Notes", notes);

        show(main, original == null ? "Add assignment" : "Edit assignment", grid, "Save", () -> {
            Models.Course chosen = course.getValue();
            var input = new Forms.AssignmentInput(title.getText(),
                    chosen == null || chosen == NO_COURSE ? null : chosen.id(),
                    typedDate(date), time.getText(), hours.getText(), spent.getText(),
                    remind.getText(), notes.getText());
            Map<String, Object> body = Forms.assignmentBody(input, original, main.zone);
            return () -> {
                if (original == null) {
                    main.app.service.addAssignment(body);
                } else {
                    main.app.service.updateAssignment(original.id(), body);
                }
                return null;
            };
        });
    }

    static void course(MainView main, Models.Course original) {
        TextField code = field("c-code", original == null ? "" : original.code());
        TextField name = field("c-name", original == null ? "" : original.name());
        code.setPromptText("for example CS101");
        GridPane grid = grid();
        add(grid, 0, "Code", code);
        add(grid, 1, "Name", name);
        show(main, original == null ? "Add course" : "Edit course", grid, "Save", () -> {
            String newCode = code.getText().strip();
            String newName = name.getText().strip();
            if (newCode.isEmpty() || newName.isEmpty()) {
                throw new IllegalArgumentException("Enter a code and a name");
            }
            return () -> {
                if (original == null) {
                    main.app.service.addCourse(newCode, newName);
                } else {
                    main.app.service.updateCourse(original.id(), newCode, newName);
                }
                return null;
            };
        });
    }

    static void logTime(MainView main, Models.Assignment assignment) {
        TextField hours = field("f-log", "1");
        GridPane grid = grid();
        grid.add(Widgets.wrapped(assignment.title() + "\n"
                + Forms.hours(assignment.hoursSpent()) + " spent, "
                + Forms.hours(assignment.remainingHours()) + " left"), 0, 0, 2, 1);
        add(grid, 1, "Hours worked", hours);
        show(main, "Log time", grid, "Log", () -> {
            double worked = Parse.number(hours.getText().strip(), "Hours worked");
            return () -> {
                main.app.service.logHours(assignment.id(), worked);
                return null;
            };
        });
    }

    static void settings(MainView main) {
        if (main.data == null) {
            return;
        }
        TextField daily = field("s-daily", Forms.number(main.data.user().dailyHours()));
        TextField remind = field("s-remind", String.valueOf(main.data.user().reminderHours()));
        GridPane grid = grid();
        add(grid, 0, "Study hours per day", daily);
        add(grid, 1, "Remind (hours before)", remind);
        show(main, "Settings", grid, "Save", () -> {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("daily_study_hours",
                    Parse.number(daily.getText().strip(), "Study hours per day"));
            changes.put("reminder_hours",
                    Parse.wholeNumber(remind.getText().strip(), "Reminder hours"));
            return () -> {
                main.app.service.saveSettings(changes);
                return null;
            };
        });
    }

    static void deleteAssignment(MainView main, Models.Assignment assignment) {
        confirm(main, "Delete assignment",
                "Delete '" + assignment.title() + "' permanently?", () -> {
                    main.app.service.deleteAssignment(assignment.id());
                    return null;
                });
    }

    static void deleteCourse(MainView main, Models.Course course) {
        confirm(main, "Delete course",
                "Delete the course " + course.code() + "? Its assignments are kept.", () -> {
                    main.app.service.deleteCourse(course.id());
                    return null;
                });
    }

    private static void confirm(MainView main, String title, String question,
            Callable<?> action) {
        ButtonType delete = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question,
                delete, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(main.app.stage());
        alert.getDialogPane().setId("confirm-dialog");
        // make Cancel the default button
        ((Button) alert.getDialogPane().lookupButton(delete)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL))
                .setDefaultButton(true);
        alert.setOnHidden(event -> {
            if (alert.getResult() == delete) {
                main.change(action);
            }
        });
        alert.show();
    }

    private static void show(MainView main, String title, Node content, String okText,
            Supplier<Callable<?>> prepare) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initOwner(main.app.stage());
        dialog.setResizable(true);
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(GuiApp.stylesheet());

        Label error = Widgets.wrapped("", "error");
        error.setId("f-error");
        error.setMaxWidth(380);
        Widgets.setShown(error, false);
        dialog.getDialogPane().setContent(new VBox(10, content, error));

        Button button = (Button) dialog.getDialogPane().lookupButton(ok);
        button.setId("f-ok");
        button.addEventFilter(ActionEvent.ACTION, event -> {
            // don't close the dialog until saving works
            event.consume();
            Callable<?> request;
            try {
                request = prepare.get();
            } catch (IllegalArgumentException e) {
                showError(dialog, error, e.getMessage());
                return;
            }
            button.setDisable(true);
            main.app.tasks.run(request, done -> {
                dialog.setResult(ButtonType.OK);
                dialog.close();
                main.refresh();
            }, failure -> {
                if (main.app.handleExpiredSession(failure)) {
                    dialog.setResult(ButtonType.CANCEL);
                    dialog.close();
                    return;
                }
                button.setDisable(false);
                showError(dialog, error, GuiApp.describe(failure));
            });
        });
        dialog.show();
        // focus the first text field
        Platform.runLater(() -> {
            Node first = content.lookup(".text-field");
            if (first != null) {
                first.requestFocus();
            }
        });
    }

    private static void showError(Dialog<?> dialog, Label label, String message) {
        label.setText(message);
        Widgets.setShown(label, true);
        dialog.getDialogPane().getScene().getWindow().sizeToScene();
    }

    private static DatePicker datePicker() {
        DatePicker picker = new DatePicker();
        picker.setId("f-date");
        picker.setPromptText("2026-12-31");
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return text == null || text.isBlank()
                            ? null : LocalDate.parse(text.strip());
                } catch (DateTimeParseException e) {
                    return picker.getValue();
                }
            }
        });
        return picker;
    }

    private static LocalDate typedDate(DatePicker picker) {
        String text = picker.getEditor().getText().strip();
        return text.isEmpty() ? null : Parse.date(text);
    }

    private static TextField field(String id, String value) {
        TextField field = new TextField(value);
        field.setId(id);
        field.setPrefColumnCount(24);
        return field;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }

    private static void add(GridPane grid, int row, String name, Node control) {
        Label label = new Label(name);
        label.setMinWidth(Label.USE_PREF_SIZE);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
    }
}
