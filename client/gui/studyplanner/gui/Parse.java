package studyplanner.gui;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// Input checking for the forms
class Parse {

    private Parse() {
    }

    static long wholeNumber(String text, String what) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    what + " must be a whole number, not '" + text + "'");
        }
    }

    static double number(String text, String what) {
        try {
            double value = Double.parseDouble(text);
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException e) {
            // Reported below.
        }
        throw new IllegalArgumentException(what + " must be a number, not '" + text + "'");
    }

    static LocalDate date(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Dates look like 2026-12-31, not '" + text + "'");
        }
    }
}
