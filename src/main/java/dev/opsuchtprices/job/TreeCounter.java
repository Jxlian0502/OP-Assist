package dev.opsuchtprices.job;

/**
 * Erkennt gefällte Bäume: Nach dem Anschlagen eines Stamms schickt der Server bei Baumfäller-Äxten für jeden
 * Block eine eigene Holzfäller-Actionbar. Kommen innerhalb von {@link #WINDOW_MS} nach dem Anschlagen mindestens
 * {@link #MIN_BARS} Meldungen, zählt das als EIN Baum. Einzelne Stämme (normale Axt) zählen nicht.
 */
public final class TreeCounter {
    public static final long WINDOW_MS = 2500;
    public static final int MIN_BARS = 2;

    private long attackAt = -1;
    private int bars;
    private boolean counted;

    /** Spieler hat einen Stamm angeschlagen. */
    public synchronized void onLogAttack(long now) {
        attackAt = now;
        bars = 0;
        counted = false;
    }

    /** Holzfäller-Actionbar kam an. Liefert true, wenn damit ein Baum fertig erkannt wurde. */
    public synchronized boolean onHolzfaellerBar(long now) {
        if (attackAt < 0 || now - attackAt > WINDOW_MS || counted) return false;
        bars++;
        if (bars >= MIN_BARS) {
            counted = true;
            return true;
        }
        return false;
    }
}
