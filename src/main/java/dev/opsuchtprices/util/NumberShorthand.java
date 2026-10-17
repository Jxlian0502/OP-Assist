package dev.opsuchtprices.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kurzschreibweise für Beträge: 1k = 1.000, 2,5m / 2.5mio = 2.500.000, 1mrd / 1b = 1.000.000.000.
 * Wird bei /pay, /bank und im Amboss (Eingabefelder des Servers) in die volle Zahl umgewandelt.
 */
public final class NumberShorthand {
    private NumberShorthand() {}

    private static final Pattern TOKEN = Pattern.compile("(?i)^(\\d+(?:[.,]\\d+)?)\\s*(k|m|mio|mrd|b)$");

    /** Volle Zahl als Text (ohne Tausenderpunkte), oder null wenn der Text keine Kurzschreibweise ist. */
    public static String expand(String text) {
        if (text == null) return null;
        Matcher m = TOKEN.matcher(text.trim());
        if (!m.matches()) return null;
        BigDecimal n = new BigDecimal(m.group(1).replace(',', '.'));
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        BigDecimal factor = switch (unit) {
            case "k" -> BigDecimal.valueOf(1_000L);
            case "m", "mio" -> BigDecimal.valueOf(1_000_000L);
            default -> BigDecimal.valueOf(1_000_000_000L);
        };
        BigDecimal v = n.multiply(factor).stripTrailingZeros();
        return v.scale() <= 0 ? v.toPlainString() : v.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * Wandelt in einem Befehl ohne "/" (z. B. "pay Max 1,5k") alle Beträge in Kurzschreibweise um,
     * aber nur bei /pay und /bank. Andere Befehle bleiben unverändert.
     */
    public static String expandCommand(String command) {
        if (command == null) return null;
        String[] parts = command.split(" ", -1);
        if (parts.length < 2) return command;
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!root.equals("pay") && !root.equals("bank")) return command;
        boolean changed = false;
        for (int i = 1; i < parts.length; i++) {
            String e = expand(parts[i]);
            if (e != null) {
                parts[i] = e;
                changed = true;
            }
        }
        return changed ? String.join(" ", parts) : command;
    }
}
