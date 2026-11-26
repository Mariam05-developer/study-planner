package studyplanner.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Data classes matching the API responses
class Models {

    record User(String username, double dailyHours, long reminderHours) {
    }

    record Course(long id, String code, String name) {
    }

    record Assignment(long id, String title, String notes, Long courseId, String courseCode,
            String dueAt, double estimatedHours, double hoursSpent, double remainingHours,
            Long reminderHours, boolean completed) {
    }

    record Ranked(long rank, double score, String risk, String reason, Assignment assignment) {
    }

    record Reminder(String kind, String message, Assignment assignment) {
    }

    record Due(long assignmentId, String title, String courseCode, String dueAt,
            boolean completed) {
    }

    record Work(long assignmentId, String title, String courseCode, double hours) {
    }

    record Day(String date, boolean today, double plannedHours, double capacityHours,
            boolean overloaded, List<Due> due, List<Work> plan) {
    }

    record Week(String start, String end, String timezone, double plannedHours,
            double capacityHours, List<Day> days) {
    }

    record Snapshot(User user, List<Course> courses, List<Assignment> assignments,
            List<Ranked> ranking, Week week, List<Reminder> reminders) {
    }

    private Models() {
    }

    static User user(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new User(text(m, "username"), number(m, "daily_study_hours"),
                whole(m, "reminder_hours"));
    }

    static Course course(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new Course(whole(m, "id"), text(m, "code"), text(m, "name"));
    }

    static Assignment assignment(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new Assignment(whole(m, "id"), text(m, "title"), text(m, "notes"),
                optionalWhole(m, "course_id"), text(m, "course_code"), text(m, "due_at"),
                number(m, "estimated_hours"), number(m, "hours_spent"),
                number(m, "remaining_hours"), optionalWhole(m, "reminder_hours"),
                flag(m, "completed"));
    }

    static Ranked ranked(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new Ranked(whole(m, "rank"), number(m, "score"), text(m, "risk"),
                text(m, "reason"), assignment(m.get("assignment")));
    }

    static Reminder reminder(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new Reminder(text(m, "kind"), text(m, "message"),
                assignment(m.get("assignment")));
    }

    static Week week(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new Week(text(m, "week_start"), text(m, "week_end"), text(m, "timezone"),
                number(m, "total_planned_hours"), number(m, "total_capacity_hours"),
                list(m.get("days"), Models::day));
    }

    private static Day day(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new Day(text(m, "date"), flag(m, "is_today"), number(m, "planned_hours"),
                number(m, "capacity_hours"), flag(m, "overloaded"),
                list(m.get("due"), item -> {
                    Map<?, ?> d = (Map<?, ?>) item;
                    return new Due(whole(d, "assignment_id"), text(d, "title"),
                            text(d, "course_code"), text(d, "due_at"), flag(d, "completed"));
                }),
                list(m.get("plan"), item -> {
                    Map<?, ?> w = (Map<?, ?>) item;
                    return new Work(whole(w, "assignment_id"), text(w, "title"),
                            text(w, "course_code"), number(w, "hours"));
                }));
    }

    static <T> List<T> list(Object json, Function<Object, T> convert) {
        List<T> result = new ArrayList<>();
        for (Object item : (List<?>) json) {
            result.add(convert.apply(item));
        }
        return List.copyOf(result);
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    private static double number(Map<?, ?> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }

    private static long whole(Map<?, ?> map, String key) {
        return ((Number) map.get(key)).longValue();
    }

    private static Long optionalWhole(Map<?, ?> map, String key) {
        return map.get(key) == null ? null : whole(map, key);
    }

    private static boolean flag(Map<?, ?> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }
}
