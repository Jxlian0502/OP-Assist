package dev.opsuchtprices.job;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Logik nach dem Jobtracker der OPMOD (MIT-Lizenz, Copyright (c) 2025 Geldbedarf &amp; CalledCracki):
 * jede Job-Actionbar addiert ihre XP und ihr Geld, Level und Prozent werden übernommen.
 *
 * Zählt Job-Belohnungen dieser Sitzung pro Job (XP, Geld, aktive Zeit, Level-Fortschritt).
 * Aktive Zeit läuft nur, solange Meldungen kommen; nach {@code pauseMs} ohne Meldung pausiert sie.
 */
public final class JobTracker {

    /** Mindestens so viel aktive Zeit im aktuellen Level, bevor eine Level-Up-Zeit geschätzt wird. */
    static final long MIN_ESTIMATE_MS = 30_000;

    public static final class Stats {
        public final String job;
        public int level;
        public Double percent;
        public double xp;
        public double money;
        public int actions;
        public int levelUps;
        long activeMs;
        long lastEventAt;
        // Basis für die Level-Up-Schätzung (zurückgesetzt bei Levelwechsel)
        int baseLevel = -1;
        double basePercent;
        long baseActiveMs;

        Stats(String job) {
            this.job = job;
        }
    }

    private final LongSupplier clock;
    /** Wird bei jeder gezählten Belohnung mit den XP aufgerufen (z. B. für den 100.000-XP-Zähler). */
    private java.util.function.DoubleConsumer xpListener = xp -> {};
    private long pauseMs;
    private final Map<String, Stats> stats = new LinkedHashMap<>();
    private String current;
    private JobParser.JobTick last;
    private long lastAt;

    public JobTracker(LongSupplier clock, long pauseMs) {
        this.clock = clock;
        this.pauseMs = pauseMs;
    }

    public void setXpListener(java.util.function.DoubleConsumer listener) {
        this.xpListener = listener == null ? xp -> {} : listener;
    }

    public void setPauseMs(long pauseMs) {
        this.pauseMs = pauseMs;
    }

    /** Verarbeitet eine Actionbar-Zeile. Liefert true, wenn es eine Job-Meldung war. */
    public synchronized boolean onActionBar(String text) {
        return onActionBar(text, 1);
    }

    /**
     * Wie {@link #onActionBar(String)}, XP und Geld zählen aber {@code multiplier}-fach
     * (Baumfäller-Werkzeug: Actionbar zeigt einen Block, bezahlt werden z. B. 17).
     */
    public synchronized boolean onActionBar(String text, int multiplier) {
        JobParser.JobTick t = JobParser.parse(text);
        if (t == null) return false;
        long now = clock.getAsLong();
        // Wie die OPMOD: jede Job-Actionbar zählt (der Server schickt bei Baumfäller-Äxten eine Meldung pro Block)
        last = t;
        lastAt = now;

        Stats s = stats.computeIfAbsent(t.job(), Stats::new);
        if (s.lastEventAt > 0) {
            long gap = now - s.lastEventAt;
            if (gap > 0 && gap <= pauseMs) s.activeMs += gap;
        }
        s.lastEventAt = now;
        if (s.baseLevel >= 0 && t.level() > s.level) s.levelUps += t.level() - s.level;
        s.level = t.level();
        s.percent = t.percent();
        int mul = Math.max(1, multiplier);
        s.xp += t.xp() * mul;
        s.money += t.money() * mul;
        s.actions++;
        if (s.baseLevel != t.level()) {
            s.baseLevel = t.level();
            s.basePercent = t.percent() == null ? 0 : t.percent();
            s.baseActiveMs = s.activeMs;
        }
        current = t.job();
        xpListener.accept(t.xp() * mul);
        return true;
    }

    public synchronized Stats current() {
        return current == null ? null : stats.get(current);
    }

    public synchronized Map<String, Stats> all() {
        return Map.copyOf(stats);
    }

    public synchronized void reset() {
        stats.clear();
        current = null;
        last = null;
    }

    /** Pausiert, wenn länger als pauseMs keine Meldung kam. */
    public synchronized boolean isPaused(Stats s) {
        return s == null || clock.getAsLong() - s.lastEventAt > pauseMs;
    }

    /** Aktive Zeit inkl. laufendem Abschnitt. */
    public synchronized long activeMs(Stats s) {
        if (s == null) return 0;
        long running = clock.getAsLong() - s.lastEventAt;
        return s.activeMs + (running > 0 && running <= pauseMs ? running : 0);
    }

    public synchronized long sinceLastEventMs(Stats s) {
        return s == null ? Long.MAX_VALUE : clock.getAsLong() - s.lastEventAt;
    }

    /** Wert pro Stunde auf Basis der aktiven Zeit; null bei weniger als einer Minute. */
    public synchronized Double perHour(Stats s, double value) {
        long ms = activeMs(s);
        return ms < 60_000 ? null : value * 3_600_000.0 / ms;
    }

    /** Geschätzte Zeit bis zum nächsten Level (ms) oder null, wenn noch zu wenig Daten. */
    public synchronized Long timeToLevelUpMs(Stats s) {
        if (s == null || s.percent == null) return null;
        long ms = activeMs(s) - s.baseActiveMs;
        double gained = s.percent - s.basePercent;
        if (ms < MIN_ESTIMATE_MS || gained <= 0) return null;
        return (long) ((100.0 - s.percent) * ms / gained);
    }
}
