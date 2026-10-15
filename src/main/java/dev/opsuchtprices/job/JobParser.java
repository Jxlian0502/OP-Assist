package dev.opsuchtprices.job;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die Job-Actionbar von OP SUCHT, z. B.
 * {@code +2,75 XP • +15,887$ • Holzfäller Level 78 • [||||||||] 56,79%}.
 * Zahlen im deutschen Format (Komma = Dezimaltrennzeichen, Punkt = Tausender).
 */
public final class JobParser {
    private JobParser() {}

    /** Eine Belohnung aus der Actionbar. xp/money sind 0, wenn nicht angegeben. */
    public record JobTick(String job, int level, double xp, double money, Double percent) {}

    private static final Pattern CODES = Pattern.compile("§[0-9a-fk-orA-FK-ORxX]");
    private static final Pattern XP = Pattern.compile("([+-]?\\d[\\d.]*(?:,\\d+)?)\\s*XP");
    private static final Pattern MONEY = Pattern.compile("([+-]?\\d[\\d.]*(?:,\\d+)?)\\s*\\$");
    private static final Pattern JOB = Pattern.compile("(\\p{L}[\\p{L}-]*)\\s+Level\\s+(\\d+)");
    private static final Pattern PERCENT = Pattern.compile("(\\d[\\d.]*(?:,\\d+)?)\\s*%");

    /** Liefert null, wenn es keine Job-Meldung ist. */
    public static JobTick parse(String raw) {
        if (raw == null) return null;
        String s = CODES.matcher(raw).replaceAll("").trim();
        Matcher job = JOB.matcher(s);
        if (!job.find()) return null;
        Matcher xp = XP.matcher(s);
        Matcher money = MONEY.matcher(s);
        boolean hasXp = xp.find(), hasMoney = money.find();
        if (!hasXp && !hasMoney) return null;
        Matcher pct = PERCENT.matcher(s);
        Double percent = pct.find() ? number(pct.group(1)) : null;
        return new JobTick(job.group(1), Integer.parseInt(job.group(2)),
                hasXp ? number(xp.group(1)) : 0, hasMoney ? number(money.group(1)) : 0, percent);
    }

    public static double number(String s) {
        return Double.parseDouble(s.replace("+", "").replace(".", "").replace(',', '.'));
    }
}
