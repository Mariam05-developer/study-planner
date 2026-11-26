package studyplanner.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

// Java version of backend/app/planner.py, used in guest mode.
// If you change one, change the other!
class LocalRules {

    // BigDecimal so it rounds the same way Python does
    static double round(double value, int places) {
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    static double remainingHours(double estimated, double spent, boolean completed) {
        if (completed) {
            return 0;
        }
        return round(Math.max(estimated - spent, 0.5), 2);
    }

    private static String formatHours(double hours) {
        return Forms.hours(round(hours, 1));
    }

    private static double secondsBetween(Instant from, Instant to) {
        Duration gap = Duration.between(from, to);
        return gap.getSeconds() + gap.getNano() / 1e9;
    }

    private static String formatDuration(double seconds) {
        seconds = Math.abs(seconds);
        long amount;
        String unit;
        if (seconds >= 48 * 3600) {
            amount = (long) (seconds / 86400);
            unit = "day";
        } else if (seconds >= 3600) {
            amount = (long) (seconds / 3600);
            unit = "hour";
        } else {
            amount = Math.max((long) (seconds / 60), 1);
            unit = "minute";
        }
        if (amount != 1) {
            unit += "s";
        }
        return amount + " " + unit;
    }

    // what to work on next

    private static Models.Ranked score(Models.Assignment a, double dailyHours, Instant now) {
        double left = a.remainingHours();
        double timeLeft = secondsBetween(now, Instant.parse(a.dueAt()));

        if (timeLeft <= 0) {
            double score = 101 + Math.min(-timeLeft / 86400, 99);
            String reason = "Overdue by " + formatDuration(timeLeft) + "; about "
                    + formatHours(left) + " of work left";
            return new Models.Ranked(0, round(score, 1), "overdue", reason, a);
        }

        double days = timeLeft / 86400;
        double available = days * dailyHours;
        double pressure = left / available;
        double closeness = 1 / (1 + days / 3);
        double score = 100 * (0.6 * Math.min(pressure, 1.0) + 0.4 * closeness);

        String risk;
        if (pressure >= 1) {
            risk = "at_risk";
        } else if (pressure >= 0.5) {
            risk = "tight";
        } else {
            risk = "on_track";
        }

        String reason = "Due in " + formatDuration(timeLeft) + "; " + formatHours(left)
                + " left vs about " + formatHours(available) + " of study time";
        return new Models.Ranked(0, round(score, 1), risk, reason, a);
    }

    static List<Models.Ranked> rank(List<Models.Assignment> assignments, double dailyHours,
            Instant now) {
        List<Models.Ranked> scored = new ArrayList<>();
        for (Models.Assignment a : assignments) {
            if (!a.completed()) {
                scored.add(score(a, dailyHours, now));
            }
        }
        scored.sort(LocalRules::compare);

        // fill in the rank numbers
        List<Models.Ranked> result = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Models.Ranked r = scored.get(i);
            result.add(new Models.Ranked(i + 1, r.score(), r.risk(), r.reason(),
                    r.assignment()));
        }
        return result;
    }

    // highest score first, ties go to the earlier due date then the bigger task
    private static int compare(Models.Ranked x, Models.Ranked y) {
        Models.Assignment a = x.assignment();
        Models.Assignment b = y.assignment();
        if (x.score() != y.score()) {
            return Double.compare(y.score(), x.score());
        }
        int byDue = Instant.parse(a.dueAt()).compareTo(Instant.parse(b.dueAt()));
        if (byDue != 0) {
            return byDue;
        }
        if (a.remainingHours() != b.remainingHours()) {
            return Double.compare(b.remainingHours(), a.remainingHours());
        }
        return Long.compare(a.id(), b.id());
    }

    // weekly workload (assignments must already be sorted by due date)

    static Models.Week week(List<Models.Assignment> assignments, double dailyHours,
            LocalDate dayInWeek, ZoneId zone, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        if (dayInWeek == null) {
            dayInWeek = today;
        }
        LocalDate monday = dayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<Models.Day> days = new ArrayList<>();
        BigDecimal weekTotal = BigDecimal.ZERO;

        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            List<Models.Due> due = new ArrayList<>();
            List<Models.Work> plan = new ArrayList<>();
            // BigDecimal to avoid floating point error, like fsum in Python
            BigDecimal dayTotal = BigDecimal.ZERO;

            for (Models.Assignment a : assignments) {
                Instant dueAt = Instant.parse(a.dueAt());
                LocalDate dueDay = dueAt.atZone(zone).toLocalDate();
                if (dueDay.equals(day)) {
                    due.add(new Models.Due(a.id(), a.title(), a.courseCode(), a.dueAt(),
                            a.completed()));
                }
                if (a.completed()) {
                    continue;
                }

                LocalDate lastDay = dueDay;
                if (!dueAt.isAfter(now) || dueDay.isBefore(today)) {
                    lastDay = today;
                }

                if (!day.isBefore(today) && !day.isAfter(lastDay)) {
                    long numDays = ChronoUnit.DAYS.between(today, lastDay) + 1;
                    double perDay = a.remainingHours() / numDays;
                    dayTotal = dayTotal.add(new BigDecimal(perDay));
                    double rounded = round(perDay, 2);
                    weekTotal = weekTotal.add(new BigDecimal(rounded));
                    plan.add(new Models.Work(a.id(), a.title(), a.courseCode(), rounded));
                }
            }

            double planned = dayTotal.doubleValue();
            boolean overloaded = planned > dailyHours + 0.000001;
            days.add(new Models.Day(day.toString(), day.equals(today), round(planned, 2),
                    dailyHours, overloaded, due, plan));
        }

        return new Models.Week(monday.toString(), monday.plusDays(6).toString(), zone.getId(),
                round(weekTotal.doubleValue(), 1), round(dailyHours * 7, 2), days);
    }

    // reminders

    static Models.Reminder reminder(Models.Assignment a, String dismissedStage,
            long defaultHours, Instant now) {
        if (a.completed()) {
            return null;
        }
        Instant due = Instant.parse(a.dueAt());
        long hoursBefore = a.reminderHours() == null ? defaultHours : a.reminderHours();

        String stage;
        if (!due.isAfter(now)) {
            stage = "overdue";
        } else if (Duration.between(now, due).compareTo(Duration.ofHours(hoursBefore)) <= 0) {
            stage = "upcoming";
        } else {
            return null;
        }

        // dismissing the "upcoming" one shouldn't hide the "overdue" one later
        if (stage.equals(dismissedStage) || "overdue".equals(dismissedStage)) {
            return null;
        }

        String gap = formatDuration(secondsBetween(now, due));
        String when = stage.equals("overdue") ? "is overdue by " + gap : "is due in " + gap;
        String message = "'" + a.title() + "' " + when + " ("
                + formatHours(a.remainingHours()) + " of work left)";
        return new Models.Reminder(stage, message, a);
    }
}
