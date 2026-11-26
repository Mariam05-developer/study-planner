package studyplanner.gui;

import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

// Courses tab
class CoursesTab extends VBox {
    private final TableView<Models.Course> table = new TableView<>();

    CoursesTab(MainView main) {
        getStyleClass().add("page");
        setSpacing(10);
        table.setId("course-table");
        column("Code", 150, Models.Course::code);
        column("Name", 500, Models.Course::name);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Widgets.label("No courses yet.", "muted"));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button add = Widgets.button("Add course", "course-add",
                () -> Dialogs.course(main, null));
        add.getStyleClass().add("primary");
        Button edit = Widgets.button("Edit", "course-edit",
                () -> withSelected(c -> Dialogs.course(main, c)));
        Button delete = Widgets.button("Delete", "course-delete",
                () -> withSelected(c -> Dialogs.deleteCourse(main, c)));
        var selection = table.getSelectionModel().selectedItemProperty();
        edit.disableProperty().bind(selection.isNull());
        delete.disableProperty().bind(selection.isNull());
        getChildren().addAll(new HBox(8, add, edit, delete), table);
    }

    private void column(String name, double width, Function<Models.Course, String> value) {
        TableColumn<Models.Course, String> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }

    private void withSelected(Consumer<Models.Course> action) {
        Models.Course selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            action.accept(selected);
        }
    }

    void show(Models.Snapshot data) {
        Models.Course selected = table.getSelectionModel().getSelectedItem();
        table.getSelectionModel().clearSelection();
        table.getItems().setAll(data.courses());
        table.sort();
        if (selected != null) {
            table.getItems().stream()
                    .filter(c -> c.id() == selected.id())
                    .findFirst()
                    .ifPresent(table.getSelectionModel()::select);
        }
    }
}
