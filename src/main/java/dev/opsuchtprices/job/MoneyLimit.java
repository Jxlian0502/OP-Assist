package dev.opsuchtprices.job;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Job-Geld-Limit von OP SUCHT (höchstens 1,5 Mio. $ pro Stunde aus Jobs). Erkennt die Server-Meldung
 * "Du hast dein Job-Geld-Limit erreicht, weiter geht es in 22 Minuten!" und merkt sich, wann das Limit
 * endet, damit die Mod einmal Bescheid gibt und noch einmal, wenn es vorbei ist.
 */
public final class MoneyLimit {
    /** Maximaler Job-Verdienst pro Stunde laut OP SUCHT (nur für die Anzeige). */
    public static final String LIMIT_TEXT = "1,5 Mio. $ pro Stunde";

    private static final Pattern WAIT = Pattern.compile("in\\s+(\\d+)\\s*(minute|minuten|min|sekunde|sekunden|sek)\\b");
    /** Gleiche Meldung innerhalb dieser Abweichung = dasselbe Limit (Server wiederholt sie). */
    private static final long SAME_LIMIT_TOLERANCE_MS = 3 * 60_000L;

    private long resetAt;
    private boolean resetPending;
    private long lastNotifyAt;

    /** true, wenn der Text die Limit-Meldung des Servers ist. */
    public static boolean isLimitMessage(String text) {
        return normalize(text).contains("job-geld-limit erreicht");
    }

    /** Wartezeit aus der Meldung in Millisekunden, oder -1 wenn keine Zeit angegeben ist. */
    public static long waitMillis(String text) {
        Matcher m = WAIT.matcher(normalize(text));
        if (!m.find()) return -1;
        long n = Long.parseLong(m.group(1));
        return m.group(2).startsWith("sek") ? n * 1000L : n * 60_000L;
    }

    /**
     * Verarbeitet eine Limit-Meldung. Liefert true, wenn die Mod Bescheid geben soll (erste Meldung
     * dieses Limits); wiederholte Meldungen aktualisieren nur die Endzeit.
     */
    public synchronized boolean onLimitMessage(String text, long now) {
        long wait = waitMillis(text);
        long newReset = wait < 0 ? 0 : now + wait;
        boolean sameLimit = resetPending && (newReset == 0 || Math.abs(newReset - resetAt) <= SAME_LIMIT_TOLERANCE_MS);
        // Meldung ohne Zeitangabe: höchstens alle 10 Minuten Bescheid geben
        if (!resetPending && newReset == 0 && lastNotifyAt > 0 && now - lastNotifyAt < 10 * 60_000L) sameLimit = true;
        if (newReset > 0) resetAt = newReset;
        resetPending = resetAt > now;
        if (!sameLimit) lastNotifyAt = now;
        return !sameLimit;
    }

    /** Einmal true, sobald die Wartezeit vorbei ist (für die Meldung "wieder Geld"). */
    public synchronized boolean pollReset(long now) {
        if (resetPending && now >= resetAt) {
            resetPending = false;
            return true;
        }
        return false;
    }

    /** Zeitpunkt, ab dem es wieder Geld gibt (0 = unbekannt). */
    public synchronized long resetAt() {
        return resetPending ? resetAt : 0;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-orA-FK-ORxX]", "").toLowerCase(Locale.ROOT);
    }
}
