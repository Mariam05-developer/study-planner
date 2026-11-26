package studyplanner.gui;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

// Common interface for server accounts and guest mode
interface Planner {

    boolean isGuest();

    String description(Models.User user);

    Models.Snapshot load(LocalDate weekOf);

    void signOut() throws IOException;

    void saveSettings(Map<String, ?> changes);

    void addCourse(String code, String name);

    void updateCourse(long id, String code, String name);

    void deleteCourse(long id);

    void addAssignment(Map<String, ?> body);

    void updateAssignment(long id, Map<String, ?> changes);

    default void setCompleted(long id, boolean completed) {
        updateAssignment(id, Map.of("completed", completed));
    }

    void logHours(long id, double hours);

    void dismissReminder(long id);

    void deleteAssignment(long id);
}
