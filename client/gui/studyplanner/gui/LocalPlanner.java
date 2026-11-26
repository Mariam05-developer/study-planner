package studyplanner.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import studyplanner.Config;

// Guest mode: everything is stored in a local file, no server needed.
// Each method loads the file, changes it and saves it again.
class LocalPlanner implements Planner {
    static final String FILE_NAME = "guest.json";

    private final Config config;
    private final Path file;
    private final Clock clock;

    LocalPlanner(Config config, Path file, Clock clock) {
        this.config = config;
        this.file = file;
        this.clock = clock;
    }

    void start() throws IOException {
        LocalData.read(file); // fails here if the file is broken
        config.setGuest(true);
    }

    @Override
    public boolean isGuest() {
        return true;
    }

    @Override
    public String description(Models.User user) {
        return "Guest - saved on this computer";
    }

    @Override
    public void signOut() throws IOException {
        config.setGuest(false);
    }

    @Override
    public Models.Snapshot load(LocalDate weekOf) {
        LocalData data = LocalData.read(file);
        Instant now = clock.instant();

        List<Models.Course> courses = new ArrayList<>(data.courses);
        courses.sort(Comparator.comparing(Models.Course::code));

        data.assignments.sort((a, b) -> {
            int result = a.dueAt.compareTo(b.dueAt);
            return result != 0 ? result : Long.compare(a.id, b.id);
        });

        List<Models.Assignment> assignments = new ArrayList<>();
        List<Models.Reminder> reminders = new ArrayList<>();
        for (LocalData.Item item : data.assignments) {
            Models.Assignment a = toAssignment(data, item);
            assignments.add(a);
            Models.Reminder reminder = LocalRules.reminder(a, item.dismissedStage,
                    data.reminderHours, now);
            if (reminder != null) {
                reminders.add(reminder);
            }
        }

        Models.User user = new Models.User("Guest", data.dailyStudyHours, data.reminderHours);
        return new Models.Snapshot(user, courses, assignments,
                LocalRules.rank(assignments, data.dailyStudyHours, now),
                LocalRules.week(assignments, data.dailyStudyHours, weekOf, clock.getZone(), now),
                reminders);
    }

    @Override
    public void saveSettings(Map<String, ?> changes) {
        LocalData data = LocalData.read(file);
        if (changes.containsKey("daily_study_hours")) {
            double hours = toDouble(changes.get("daily_study_hours"));
            check(hours > 0 && hours <= 24, "Study hours per day must be between 0 and 24");
            data.dailyStudyHours = hours;
        }
        if (changes.containsKey("reminder_hours")) {
            data.reminderHours = checkReminderHours(changes.get("reminder_hours"));
        }
        data.write(file);
    }

    // courses

    @Override
    public void addCourse(String code, String name) {
        LocalData data = LocalData.read(file);
        data.courses.add(makeCourse(data, data.nextCourseId, code, name));
        data.nextCourseId++;
        data.write(file);
    }

    @Override
    public void updateCourse(long id, String code, String name) {
        LocalData data = LocalData.read(file);
        int index = data.courses.indexOf(findCourse(data, id));
        data.courses.set(index, makeCourse(data, id, code, name));
        data.write(file);
    }

    @Override
    public void deleteCourse(long id) {
        LocalData data = LocalData.read(file);
        data.courses.remove(findCourse(data, id));
        for (LocalData.Item item : data.assignments) {
            if (item.courseId != null && item.courseId == id) {
                item.courseId = null;
            }
        }
        data.write(file);
    }

    private Models.Course makeCourse(LocalData data, long id, String code, String name) {
        code = code.strip().toUpperCase();
        name = name.strip();
        check(!code.isEmpty() && code.length() <= 20, "Course code must be 1-20 characters");
        check(!name.isEmpty() && name.length() <= 100, "Course name must be 1-100 characters");
        for (Models.Course other : data.courses) {
            check(other.id() == id || !other.code().equals(code),
                    "You already have a course with that code");
        }
        return new Models.Course(id, code, name);
    }

    // assignments

    @Override
    public void addAssignment(Map<String, ?> body) {
        check(body.get("title") != null && body.get("due_at") != null
                && body.get("estimated_hours") != null,
                "A title, due date and estimated hours are required");
        LocalData data = LocalData.read(file);
        LocalData.Item item = new LocalData.Item();
        item.id = data.nextAssignmentId;
        data.nextAssignmentId++;
        applyChanges(data, item, body);
        data.assignments.add(item);
        data.write(file);
    }

    @Override
    public void updateAssignment(long id, Map<String, ?> changes) {
        LocalData data = LocalData.read(file);
        LocalData.Item item = findAssignment(data, id);
        Instant oldDue = item.dueAt;
        Long oldReminder = item.reminderHours;
        applyChanges(data, item, changes);
        // show the reminder again if the deadline or reminder time changed
        if (!item.dueAt.equals(oldDue) || !Objects.equals(item.reminderHours, oldReminder)) {
            item.dismissedStage = null;
        }
        data.write(file);
    }

    // uses the same field names as the API
    private void applyChanges(LocalData data, LocalData.Item item, Map<String, ?> changes) {
        if (changes.containsKey("title")) {
            String title = ((String) changes.get("title")).strip();
            check(!title.isEmpty() && title.length() <= 200, "Title must be 1-200 characters");
            item.title = title;
        }
        if (changes.containsKey("notes")) {
            String notes = (String) changes.get("notes");
            check(notes.length() <= 2000, "Notes can be at most 2000 characters");
            item.notes = notes;
        }
        if (changes.containsKey("due_at")) {
            OffsetDateTime due = OffsetDateTime.parse((String) changes.get("due_at"));
            check(due.getYear() >= 2000 && due.getYear() <= 2200,
                    "The due date must be between the years 2000 and 2200");
            item.dueAt = due.toInstant();
        }
        if (changes.containsKey("estimated_hours")) {
            double hours = toDouble(changes.get("estimated_hours"));
            check(hours > 0 && hours <= 1000, "Estimated hours must be between 0 and 1000");
            item.estimatedHours = hours;
        }
        if (changes.containsKey("hours_spent")) {
            double hours = toDouble(changes.get("hours_spent"));
            check(hours >= 0 && hours <= 10000, "Hours spent must be between 0 and 10000");
            item.hoursSpent = hours;
        }
        if (changes.containsKey("course_id")) {
            Number courseId = (Number) changes.get("course_id");
            item.courseId = courseId == null
                    ? null : findCourse(data, courseId.longValue()).id();
        }
        if (changes.containsKey("reminder_hours")) {
            Object value = changes.get("reminder_hours");
            item.reminderHours = value == null ? null : checkReminderHours(value);
        }
        if (changes.containsKey("completed")) {
            boolean completed = (Boolean) changes.get("completed");
            if (!completed) {
                item.completedAt = null;
            } else if (item.completedAt == null) {
                item.completedAt = clock.instant();
            }
        }
    }

    @Override
    public void logHours(long id, double hours) {
        check(hours > 0 && hours <= 24, "Hours worked must be between 0 and 24");
        LocalData data = LocalData.read(file);
        LocalData.Item item = findAssignment(data, id);
        item.hoursSpent = LocalRules.round(Math.min(item.hoursSpent + hours, 10000), 2);
        data.write(file);
    }

    @Override
    public void dismissReminder(long id) {
        LocalData data = LocalData.read(file);
        LocalData.Item item = findAssignment(data, id);
        Models.Reminder reminder = LocalRules.reminder(toAssignment(data, item),
                item.dismissedStage, data.reminderHours, clock.instant());
        check(reminder != null, "That assignment has no active reminder");
        item.dismissedStage = reminder.kind();
        data.write(file);
    }

    @Override
    public void deleteAssignment(long id) {
        LocalData data = LocalData.read(file);
        data.assignments.remove(findAssignment(data, id));
        data.write(file);
    }

    // helpers

    private static Models.Assignment toAssignment(LocalData data, LocalData.Item item) {
        String code = "";
        if (item.courseId != null) {
            code = findCourse(data, item.courseId).code();
        }
        boolean completed = item.completedAt != null;
        double remaining = LocalRules.remainingHours(item.estimatedHours, item.hoursSpent,
                completed);
        return new Models.Assignment(item.id, item.title, item.notes, item.courseId, code,
                item.dueAt.toString(), item.estimatedHours, item.hoursSpent, remaining,
                item.reminderHours, completed);
    }

    private static Models.Course findCourse(LocalData data, long id) {
        for (Models.Course course : data.courses) {
            if (course.id() == id) {
                return course;
            }
        }
        throw new IllegalArgumentException("Course not found");
    }

    private static LocalData.Item findAssignment(LocalData data, long id) {
        for (LocalData.Item item : data.assignments) {
            if (item.id == id) {
                return item;
            }
        }
        throw new IllegalArgumentException("Assignment not found");
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    private static double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    private static long checkReminderHours(Object value) {
        long hours = ((Number) value).longValue();
        check(hours >= 0 && hours <= 1440, "Reminder hours must be between 0 and 1440");
        return hours;
    }
}
