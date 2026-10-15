package dev.opsuchtprices.job;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Erkennt die OP SUCHT-Welt (Scoreboard "FARMWELT 1" oder Chat "Verbinde zu FARMWELT 1..."). */
public final class WorldFilter {
    private WorldFilter() {}

    private static final Pattern WORLD = Pattern.compile("\\b(FARMWELT|NETHER)\\s*(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONNECT = Pattern.compile("Verbinde zu\\s+([A-Za-zÄÖÜäöü0-9 _-]+?)\\s*(?:\\.\\.\\.|…|$)");

    /** "Farmwelt 1" / "Nether 2" aus einem Text oder null. */
    public static String find(String text) {
        if (text == null) return null;
        Matcher m = WORLD.matcher(text.replaceAll("§[0-9a-fk-orA-FK-ORxX]", ""));
        if (!m.find()) return null;
        String w = m.group(1).toUpperCase(Locale.ROOT);
        return (w.equals("FARMWELT") ? "Farmwelt " : "Nether ") + Integer.parseInt(m.group(2));
    }

    /** Ziel aus "Verbinde zu FARMWELT 1..." (beliebige Welt, z. B. "REDSTONE"), sonst null. */
    public static String connectTarget(String text) {
        if (text == null) return null;
        Matcher m = CONNECT.matcher(text.replaceAll("§[0-9a-fk-orA-FK-ORxX]", ""));
        return m.find() ? m.group(1).trim() : null;
    }

    /** Zählen erlaubt: Farmwelt 1–12 oder Nether 1–2. */
    public static boolean allowed(String world) {
        if (world == null) return false;
        Matcher m = WORLD.matcher(world);
        if (!m.find()) return false;
        int n = Integer.parseInt(m.group(2));
        return m.group(1).equalsIgnoreCase("FARMWELT") ? n >= 1 && n <= 12 : n >= 1 && n <= 2;
    }
}
