package studyplanner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

// Date formatting
public class Dates {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private Dates() {
    }

    public static String toDue(LocalDate date, String time, ZoneId zone) {
        try {
            return date.atTime(LocalTime.parse(time, TIME)).atZone(zone).toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Times look like 17:00 or 9:30, not '" + time + "'");
        }
    }

    public static String when(String iso, ZoneId zone) {
        return OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(WHEN);
    }

    public static String clock(String iso, ZoneId zone) {
        return OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(CLOCK);
    }

    public static String day(String isoDate) {
        return LocalDate.parse(isoDate).format(DAY);
    }
}
