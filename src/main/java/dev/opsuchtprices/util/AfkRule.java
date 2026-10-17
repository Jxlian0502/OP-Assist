package dev.opsuchtprices.util;

/**
 * Wann die AFK-Warnung kommt: nur wenn man vor dem Stillstand einen Job gemacht hat und seitdem
 * weder bewegt noch Job-Meldungen bekommen hat (wer still steht und z. B. angelt, ist nicht AFK).
 */
public final class AfkRule {
    private AfkRule() {}

    /** Job-Meldung höchstens so lange vor dem Stillstand gilt als "vorher einen Job gemacht". */
    public static final long JOB_BEFORE_IDLE_MS = 2 * 60_000L;

    /**
     * @param now          jetzt (ms)
     * @param lastMove     letzte Bewegung (ms)
     * @param jobEventAgo  wie lange die letzte Job-Meldung her ist (ms), null = noch kein Job
     * @param afkMs        Wartezeit bis zur Warnung (ms)
     */
    public static boolean shouldWarn(long now, long lastMove, Long jobEventAgo, long afkMs) {
        if (now - lastMove < afkMs || jobEventAgo == null) return false;
        if (jobEventAgo < afkMs) return false; // Job läuft noch (Meldungen trotz Stillstand)
        long lastJob = now - jobEventAgo;
        return lastJob >= lastMove - JOB_BEFORE_IDLE_MS;
    }
}
