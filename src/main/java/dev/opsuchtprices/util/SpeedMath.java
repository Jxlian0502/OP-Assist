package dev.opsuchtprices.util;

import java.util.List;

/**
 * Speed-Bonus aus Item-Attributen (Bewegungsgeschwindigkeit), nach derselben Formel wie Minecraft:
 * erst feste Werte addieren, dann Prozent auf die Basis, dann Faktoren auf das Gesamtergebnis.
 */
public final class SpeedMath {
    private SpeedMath() {}

    public enum Op { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }

    public record Mod(Op op, double amount) {}

    /** Bonus in Prozent gegenüber der Basis-Geschwindigkeit (z. B. 230 für "+230 %"). */
    public static double percent(double base, List<Mod> mods) {
        if (!(base > 0)) return 0;
        double v = base;
        for (Mod m : mods) if (m.op() == Op.ADD_VALUE) v += m.amount();
        double w = v;
        for (Mod m : mods) if (m.op() == Op.ADD_MULTIPLIED_BASE) w += v * m.amount();
        for (Mod m : mods) if (m.op() == Op.ADD_MULTIPLIED_TOTAL) w *= 1 + m.amount();
        return (w / base - 1) * 100;
    }

    /**
     * Speed wie bei OP SUCHT angezeigt (gleicher Wert wie die OPMOD): Minecraft-Formel der Item-Attribute,
     * wobei der Schnelligkeits-Effekt (+20 % pro Stufe) wie ein weiterer Prozent-Bonus auf die
     * Grundgeschwindigkeit zählt. Gemessen am 17.10.: (0,1 + 0,1) x (1 + 25 % + 40 %) = +230 %,
     * (0,1 + 0,1) x (1 + 160 % + 40 %) = +500 %.
     */
    public static double opSuchtPercent(double base, List<Mod> itemMods, int speedLevel) {
        List<Mod> all = new java.util.ArrayList<>(itemMods);
        if (speedLevel > 0) all.add(new Mod(Op.ADD_MULTIPLIED_BASE, 0.2 * speedLevel));
        return percent(base, all);
    }

    /** "+230%" / "-15%" / "+0%". */
    public static String format(double percent) {
        long p = Math.round(percent);
        return (p >= 0 ? "+" : "") + p + "%";
    }
}
