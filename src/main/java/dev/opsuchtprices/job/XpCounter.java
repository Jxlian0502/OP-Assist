package dev.opsuchtprices.job;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.opsuchtprices.util.AtomicFiles;
import dev.opsuchtprices.util.ModLog;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Zählt Job-XP bis zu einer Grenze (Standard 100.000), danach beginnt er wieder bei 0
 * und zählt die vollen Runden. Wird in job-zaehler.json gespeichert (bleibt über Neustarts).
 */
public final class XpCounter {

    /** Gespeicherter Zustand. */
    static final class State {
        double current;
        int rounds;
        double totalXp;
        /** Wie oft "Du hast einen Jobschatz erhalten!" kam. Wird nie zurückgesetzt. */
        int found;
        long lastFoundAt;
        /** Gefällte Bäume insgesamt. Wird nie zurückgesetzt. */
        long trees;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private State state = new State();
    private double step;
    private boolean dirty;

    public XpCounter(Path file, double step) {
        this.file = file;
        this.step = step > 0 ? step : 100_000;
    }

    public static XpCounter load(Path file, double step) {
        XpCounter c = new XpCounter(file, step);
        if (file != null) {
            try {
                String json = AtomicFiles.readStringOrNull(file);
                if (json != null) {
                    State s = GSON.fromJson(json, State.class);
                    if (s != null) c.state = s;
                }
            } catch (IOException | RuntimeException e) {
                ModLog.warn("XP-Zähler {} unlesbar, beginne bei 0: {}", file, e.toString());
            }
        }
        c.state.current = Math.max(0, c.state.current);
        return c;
    }

    /** Addiert XP. Liefert die Anzahl der dabei vollendeten Runden (meist 0, sonst 1). */
    public synchronized int add(double xp) {
        if (xp <= 0) return 0;
        state.totalXp += xp;
        state.current += xp;
        int done = 0;
        while (state.current >= step) {
            state.current -= step;
            state.rounds++;
            done++;
        }
        dirty = true;
        return done;
    }

    public synchronized double current() {
        return state.current;
    }

    public synchronized int rounds() {
        return state.rounds;
    }

    public synchronized double totalXp() {
        return state.totalXp;
    }

    public synchronized double step() {
        return step;
    }

    public synchronized void setStep(double step) {
        if (step > 0) this.step = step;
    }

    /** Setzt den Zähler (und die Runden) auf 0. Die Anzahl gefundener Jobschätze bleibt. */
    public synchronized void reset() {
        State fresh = new State();
        fresh.found = state.found;
        fresh.lastFoundAt = state.lastFoundAt;
        fresh.totalXp = state.totalXp;
        fresh.trees = state.trees;
        state = fresh;
        dirty = true;
    }

    /**
     * Jobschatz gefunden: Zähler auf 0, Fundzähler +1. Doppelte Meldungen innerhalb von 10 s zählen einmal.
     * Liefert true, wenn gezählt wurde.
     */
    public synchronized boolean treasureFound(long now) {
        if (state.lastFoundAt > 0 && now - state.lastFoundAt < 10_000) return false;
        state.current = 0;
        state.found++;
        state.lastFoundAt = now;
        dirty = true;
        return true;
    }

    /** Ein Baum gefällt (dauerhaft gezählt). */
    public synchronized void addTree() {
        state.trees++;
        dirty = true;
    }

    public synchronized long trees() {
        return state.trees;
    }

    public synchronized int found() {
        return state.found;
    }

    /** Erkennt die Server-Meldung "Du hast einen Jobschatz erhalten!". */
    public static boolean isTreasureMessage(String text) {
        if (text == null) return false;
        String s = text.replaceAll("§[0-9a-fk-orA-FK-ORxX]", "").toLowerCase(java.util.Locale.ROOT);
        return s.contains("jobschatz erhalten");
    }

    public synchronized void saveIfDirty() {
        if (!dirty || file == null) return;
        try {
            AtomicFiles.writeString(file, GSON.toJson(state));
            dirty = false;
        } catch (IOException e) {
            ModLog.warn("XP-Zähler konnte nicht gespeichert werden: {}", e.toString());
        }
    }
}
