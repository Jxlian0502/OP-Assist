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

    /** Schnelligkeits-Effekt in Prozent wie in Minecraft: +20 % pro Stufe, mal Faktor ("x2 Speed Effekt"). */
    public static double effectPercent(int speedLevel, int multiplier) {
        return Math.max(0, speedLevel) * 20.0 * Math.max(1, multiplier);
    }

    private static final java.util.regex.Pattern EFFECT_MULTIPLIER =
            java.util.regex.Pattern.compile("x\\s*(\\d+)\\s*speed[\\s-]*effekt", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Faktor aus Lore-Zeilen wie "➜ Effekt: x2 Speed Effekt" (OP SUCHT Speed Boots); 1 wenn keiner. */
    public static int effectMultiplier(Iterable<String> loreLines) {
        int best = 1;
        for (String line : loreLines) {
            if (line == null) continue;
            java.util.regex.Matcher m = EFFECT_MULTIPLIER.matcher(line.replaceAll("§[0-9a-fk-orA-FK-ORxX]", ""));
            while (m.find()) best = Math.max(best, Math.min(10, Integer.parseInt(m.group(1))));
        }
        return best;
    }

    /**
     * Speed wie bei OP SUCHT angezeigt (gleicher Wert wie die OPMOD): Item-Speed plus Schnelligkeits-Effekt,
     * der Effekt mit dem Faktor aus "x2 Speed Effekt"-Items. Beispiel: Items +150 %, Schnelligkeit II mit
     * x2-Stiefeln (+80 %) = +230 %.
     */
    public static double opSuchtPercent(double itemPercent, int speedLevel, int multiplier) {
        return itemPercent + effectPercent(speedLevel, multiplier);
    }

    /** "+230%" / "-15%" / "+0%". */
    public static String format(double percent) {
        long p = Math.round(percent);
        return (p >= 0 ? "+" : "") + p + "%";
    }
}
