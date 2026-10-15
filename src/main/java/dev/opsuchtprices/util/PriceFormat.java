package dev.opsuchtprices.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Formatierung von Preisen und Zeitangaben im deutschen Format. */
public final class PriceFormat {
    private PriceFormat() {}

    private static final DecimalFormatSymbols DE = DecimalFormatSymbols.getInstance(Locale.GERMANY);

    /** 125000 -> "125.000", 9.375 -> "9,38". Ab 100 ohne Nachkommastellen. */
    public static String number(double value) {
        DecimalFormat f = Math.abs(value) >= 100
                ? new DecimalFormat("#,##0", DE)
                : new DecimalFormat("#,##0.##", DE);
        return f.format(value);
    }

    /** Exakter Preis mit bis zu 2 Nachkommastellen, z. B. "9.599,7$" (für Kauf/Verkauf). */
    public static String exact(double value, String currency) {
        String n = new DecimalFormat("#,##0.##", DE).format(value);
        if (currency == null || currency.isBlank()) return n;
        String c = currency.trim();
        return c.length() == 1 ? n + c : n + " " + c;
    }

    public static String price(double value, String currency) {
        String n = number(value);
        if (currency == null || currency.isBlank()) return n;
        String c = currency.trim();
        // Symbole wie "$" direkt anhängen (wie auf OP SUCHT: "800$"), Wörter mit Leerzeichen
        return c.length() == 1 ? n + c : n + " " + c;
    }

    /** "gerade eben", "vor 8 Minuten", "vor 3 Std.", "vor 2 Tagen". */
    public static String ago(Instant then, Instant now) {
        if (then == null) {
            return "unbekannt";
        }
        long s = Math.max(0, Duration.between(then, now).getSeconds());
        if (s < 60) return "gerade eben";
        long m = s / 60;
        if (m < 60) return "vor " + m + (m == 1 ? " Minute" : " Minuten");
        long h = m / 60;
        if (h < 48) return "vor " + h + " Std.";
        long d = h / 24;
        return "vor " + d + " Tagen";
    }

    /** Restlaufzeit: "noch 2 Std. 5 Min.", "noch 4 Min.", "abgelaufen". */
    public static String remaining(Instant end, Instant now) {
        if (end == null) {
            return "unbekannt";
        }
        long s = Duration.between(now, end).getSeconds();
        if (s <= 0) return "abgelaufen";
        long m = s / 60;
        if (m < 1) return "noch <1 Min.";
        long h = m / 60;
        long d = h / 24;
        if (d >= 1) return "noch " + d + " T. " + (h % 24) + " Std.";
        if (h >= 1) return "noch " + h + " Std. " + (m % 60) + " Min.";
        return "noch " + m + " Min.";
    }
}
