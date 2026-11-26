package studyplanner.gui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studyplanner.Json;

// Guest mode data and how it's saved to guest.json
class LocalData {

    static class Item {
        long id;
        String title;
        String notes = "";
        Long courseId;
        Instant dueAt;
        double estimatedHours;
        double hoursSpent;
        Instant completedAt;
        Long reminderHours; // null = use the setting
        String dismissedStage;
    }

    double dailyStudyHours = 3;
    long reminderHours = 48;
    long nextCourseId = 1;
    long nextAssignmentId = 1;
    List<Models.Course> courses = new ArrayList<>();
    List<Item> assignments = new ArrayList<>();

    static LocalData read(Path file) {
        if (!Files.exists(file)) {
            return new LocalData(); // first time
        }
        try {
            return fromJson(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (RuntimeException e) {
            // don't overwrite a broken file, the user might be able to fix it
            throw new UncheckedIOException(file + " is damaged. It has been left unchanged.",
                    new IOException(e));
        }
    }

    void write(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            // write to a temp file first so a crash can't leave half a file
            Path temp = file.resolveSibling("guest.tmp");
            Files.writeString(temp, toJson());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file, e);
        }
    }

    private String toJson() {
        List<Object> courseList = new ArrayList<>();
        for (Models.Course c : courses) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.id());
            map.put("code", c.code());
            map.put("name", c.name());
            courseList.add(map);
        }

        List<Object> assignmentList = new ArrayList<>();
        for (Item a : assignments) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.id);
            map.put("title", a.title);
            map.put("notes", a.notes);
            map.put("course_id", a.courseId);
            map.put("due_at", a.dueAt.toString());
            map.put("estimated_hours", a.estimatedHours);
            map.put("hours_spent", a.hoursSpent);
            map.put("completed_at", a.completedAt == null ? null : a.completedAt.toString());
            map.put("reminder_hours", a.reminderHours);
            map.put("dismissed_stage", a.dismissedStage);
            assignmentList.add(map);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("daily_study_hours", dailyStudyHours);
        root.put("reminder_hours", reminderHours);
        root.put("next_course_id", nextCourseId);
        root.put("next_assignment_id", nextAssignmentId);
        root.put("courses", courseList);
        root.put("assignments", assignmentList);
        return Json.write(root);
    }

    private static LocalData fromJson(String text) {
        Map<?, ?> root = (Map<?, ?>) Json.parse(text);
        if (toLong(root.get("version")) != 1) {
            throw new IllegalArgumentException("unknown version");
        }

        LocalData data = new LocalData();
        data.dailyStudyHours = toDouble(root.get("daily_study_hours"));
        data.reminderHours = toLong(root.get("reminder_hours"));
        data.nextCourseId = toLong(root.get("next_course_id"));
        data.nextAssignmentId = toLong(root.get("next_assignment_id"));

        for (Object o : (List<?>) root.get("courses")) {
            data.courses.add(Models.course(o));
        }

        for (Object o : (List<?>) root.get("assignments")) {
            Map<?, ?> map = (Map<?, ?>) o;
            Item item = new Item();
            item.id = toLong(map.get("id"));
            item.title = map.get("title").toString();
            item.notes = map.get("notes").toString();
            if (map.get("course_id") != null) {
                item.courseId = toLong(map.get("course_id"));
            }
            item.dueAt = Instant.parse(map.get("due_at").toString());
            item.estimatedHours = toDouble(map.get("estimated_hours"));
            item.hoursSpent = toDouble(map.get("hours_spent"));
            if (map.get("completed_at") != null) {
                item.completedAt = Instant.parse(map.get("completed_at").toString());
            }
            if (map.get("reminder_hours") != null) {
                item.reminderHours = toLong(map.get("reminder_hours"));
            }
            item.dismissedStage = (String) map.get("dismissed_stage");
            data.assignments.add(item);
        }
        return data;
    }

    private static double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }
}
