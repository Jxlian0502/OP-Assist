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

    /** "+230%" / "-15%" / "+0%". */
    public static String format(double percent) {
        long p = Math.round(percent);
        return (p >= 0 ? "+" : "") + p + "%";
    }
}
