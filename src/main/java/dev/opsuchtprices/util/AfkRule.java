package dev.opsuchtprices.util;

/**
 * Wann die AFK-Warnung kommt. OP SUCHT zählt nur die Bewegung: wer sich 5 Minuten nicht bewegt, gilt als
 * AFK und bekommt kein Jobgeld mehr – auch beim Angeln im Stehen. Die Mod warnt deshalb nach der
 * eingestellten Zeit ohne Bewegung (Standard 4 Minuten), aber nur, wenn man gerade einen Job macht oder
 * kurz vor dem Stillstand einen gemacht hat.
 */
public final class AfkRule {
    private AfkRule() {}

    /** Job-Meldung höchstens so lange vor dem Stillstand gilt als "vorher einen Job gemacht". */
    public static final long JOB_BEFORE_IDLE_MS = 2 * 60_000L;

    /**
     * @param now          jetzt (ms)
     * @param lastMove     letzte Bewegung (ms)
     * @param jobEventAgo  wie lange die letzte Job-Meldung her ist (ms), null = noch kein Job
     * @param afkMs        Zeit ohne Bewegung bis zur Warnung (ms)
     */
    public static boolean shouldWarn(long now, long lastMove, Long jobEventAgo, long afkMs) {
        if (now - lastMove < afkMs || jobEventAgo == null) return false;
        long lastJob = now - jobEventAgo;
        // Job während des Stillstands (z. B. Angeln) oder kurz davor
        return lastJob >= lastMove - JOB_BEFORE_IDLE_MS;
    }
}
