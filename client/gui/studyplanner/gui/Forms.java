package studyplanner.gui;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import studyplanner.Dates;

// Turns what the user typed into the data we send
class Forms {

    record AssignmentInput(String title, Long courseId, LocalDate date, String time,
            String hours, String spent, String remind, String notes) {
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Forms() {
    }

    static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    static String hours(double value) {
        return number(value) + "h";
    }

    static AssignmentInput inputFor(Models.Assignment a, ZoneId zone) {
        var due = OffsetDateTime.parse(a.dueAt()).atZoneSameInstant(zone);
        return new AssignmentInput(a.title(), a.courseId(), due.toLocalDate(),
                due.format(TIME), number(a.estimatedHours()), number(a.hoursSpent()),
                a.reminderHours() == null ? "" : a.reminderHours().toString(), a.notes());
    }

    static Map<String, Object> assignmentBody(AssignmentInput input,
            Models.Assignment original, ZoneId zone) {
        if (input.title().isBlank()) {
            throw new IllegalArgumentException("Enter a title");
        }
        if (input.date() == null) {
            throw new IllegalArgumentException("Choose a due date");
        }
        String time = input.time().isBlank() ? "23:59" : input.time().strip();
        String due = Dates.toDue(input.date(), time, zone);
        double estimate = Parse.number(input.hours().strip(), "Estimated hours");
        String remindText = input.remind().strip();
        Long remind = remindText.isEmpty()
                ? null : Parse.wholeNumber(remindText, "Reminder hours");

        AssignmentInput before = original == null ? null : inputFor(original, zone);
        Map<String, Object> body = new LinkedHashMap<>();
        if (before == null || !input.title().equals(before.title())) {
            body.put("title", input.title().strip());
        }
        if (before == null || !input.date().equals(before.date())
                || !time.equals(before.time())) {
            body.put("due_at", due);
        }
        // compare the text, otherwise rounding looks like a change
        if (before == null || !input.hours().strip().equals(before.hours())) {
            body.put("estimated_hours", estimate);
        }
        if (before != null && !input.spent().strip().equals(before.spent())) {
            body.put("hours_spent", Parse.number(input.spent().strip(), "Hours spent"));
        }
        if (before == null || !Objects.equals(input.courseId(), before.courseId())) {
            body.put("course_id", input.courseId());
        }
        if (before == null || !remindText.equals(before.remind())) {
            body.put("reminder_hours", remind);
        }
        if (before == null || !input.notes().equals(before.notes())) {
            body.put("notes", input.notes());
        }
        return body;
    }
}
