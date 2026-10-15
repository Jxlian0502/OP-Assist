package dev.opsuchtprices.matching;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalisiert Anzeigenamen und Lore für den Vergleich. */
public final class NameNormalizer {
    private NameNormalizer() {}

    private static final Pattern FORMAT_CODES = Pattern.compile("§[0-9a-fk-orA-FK-OR]|§x(§[0-9a-fA-F]){6}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Entfernt §-Farbcodes, trimmt, fasst Leerzeichen zusammen, Kleinschreibung. */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = FORMAT_CODES.matcher(s).replaceAll("");
        t = WHITESPACE.matcher(t).replaceAll(" ").trim();
        return t.toLowerCase(Locale.ROOT);
    }
}
