package dev.opsuchtprices.job;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Eingabe und Anzeige der Jobtracker-Pausezeit: "45", "45s", "1:30", "2m", "2 min", "1m30s". */
public final class PauseFormat {
    private PauseFormat() {}

    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 3600;
    /** Bereich des Schiebereglers. */
    public static final int SLIDER_MAX_SECONDS = 300;

    private static final Pattern MIN_SEC = Pattern.compile("^(\\d+)\\s*:\\s*(\\d{1,2})$");
    private static final Pattern UNITS = Pattern.compile("^(?:(\\d+)\\s*(?:m|min|minuten?))?\\s*(?:(\\d+)\\s*(?:s|sek|sekunden?)?)?$");

    /** Sekunden oder -1, wenn ungültig / außerhalb 1 s bis 60 Min. */
    public static int parse(String text) {
        if (text == null) return -1;
        String t = text.trim().toLowerCase(Locale.ROOT).replace(",", ".");
        if (t.isEmpty()) return -1;
        int sec;
        Matcher m = MIN_SEC.matcher(t);
        if (m.matches()) {
            sec = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        } else {
            m = UNITS.matcher(t);
            if (!m.matches() || (m.group(1) == null && m.group(2) == null)) return -1;
            sec = (m.group(1) == null ? 0 : Integer.parseInt(m.group(1)) * 60) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
        }
        return sec < MIN_SECONDS || sec > MAX_SECONDS ? -1 : sec;
    }

    /** 45 -> "45 s", 90 -> "1:30 Min.", 300 -> "5:00 Min." */
    public static String format(int seconds) {
        if (seconds < 60) return seconds + " s";
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60) + " Min.";
    }

    /** Für das Eingabefeld: 45 -> "45s", 90 -> "1:30". */
    public static String formatInput(int seconds) {
        if (seconds < 60) return seconds + "s";
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }
}
