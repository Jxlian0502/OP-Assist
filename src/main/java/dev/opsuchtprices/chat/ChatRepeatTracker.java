package dev.opsuchtprices.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Erkennt, ob derselbe Nachrichtentext innerhalb eines Zeitfensters (Standard 15 Min.) schon einmal im Chat stand.
 * Gezählt wird nur, wenn DERSELBE Spieler denselben Text schreibt. Spielername = letztes Wort vor
 * dem Trenner ("[VIP] Max » Text" -> "max"); Text ohne Farbcodes, Groß-/Kleinschreibung und doppelte Leerzeichen.
 */
public final class ChatRepeatTracker {

    /** Ergebnis: wie oft im Fenster (inkl. dieser), wann zuerst/zuletzt davor, Absender der ersten. */
    public record Hit(int count, long firstAt, long previousAt, String firstSender) {}

    private record Seen(long at, String sender) {}

    private static final Pattern CODES = Pattern.compile("§[0-9a-fk-orA-FK-ORxX]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** OP SUCHT: "20:15:39 » » " vor der Nachricht. */
    private static final Pattern TIME_PREFIX = Pattern.compile("^\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:»\\s*)*");
    /** OP SUCHT: " » [Copy]" am Ende. */
    private static final Pattern COPY_SUFFIX = Pattern.compile("(?:\\s*»)?\\s*\\[Copy]\\s*$", Pattern.CASE_INSENSITIVE);

    private final Map<String, Deque<Seen>> seen = new HashMap<>();
    private long windowMs;
    private int minLength;
    private long lastPrune;

    public ChatRepeatTracker(long windowMs, int minLength) {
        this.windowMs = windowMs;
        this.minLength = minLength;
    }

    public synchronized void configure(long windowMs, int minLength) {
        this.windowMs = windowMs;
        this.minLength = minLength;
    }

    /** Absender und Text einer Chatzeile; sender kann leer sein. */
    public record Parts(String sender, String body) {}

    public static Parts split(String raw) {
        String s = CODES.matcher(raw == null ? "" : raw).replaceAll("");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        s = TIME_PREFIX.matcher(s).replaceFirst("");
        s = COPY_SUFFIX.matcher(s).replaceFirst("").trim();
        // Vanilla-Format "<Name> Text"
        if (s.startsWith("<")) {
            int e = s.indexOf("> ");
            if (e > 1 && e <= 40) return new Parts(s.substring(1, e).trim(), s.substring(e + 2).trim());
        }
        // "»" hat Vorrang (OP SUCHT: "Rang | Name » Text"), sonst der früheste andere Trenner
        int cut = -1, len = 0;
        for (String sep : new String[]{" » ", " >> "}) {
            int i = s.indexOf(sep);
            if (i > 0 && i <= 60 && (cut < 0 || i < cut)) {
                cut = i;
                len = sep.length();
            }
        }
        if (cut < 0) {
            for (String sep : new String[]{": ", " | "}) {
                int i = s.indexOf(sep);
                if (i > 0 && i <= 48 && (cut < 0 || i < cut)) {
                    cut = i;
                    len = sep.length();
                }
            }
        }
        if (cut < 0) return new Parts("", s);
        return new Parts(s.substring(0, cut).trim(), s.substring(cut + len).trim());
    }

    /** Spielername aus dem Absender-Teil: letztes Wort, ohne Klammern/Sonderzeichen. */
    public static String playerName(String sender) {
        String[] words = sender.trim().split("\\s+");
        String last = words.length == 0 ? "" : words[words.length - 1];
        return last.replaceAll("[^A-Za-z0-9_]", "").toLowerCase(Locale.ROOT);
    }

    /** Vergleichsschlüssel (Spieler + Text) oder null, wenn der Text zu kurz ist. */
    public String keyOf(Parts p) {
        String body = SPACES.matcher(p.body().toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (body.length() < minLength) return null;
        return playerName(p.sender()) + "\u0000" + body;
    }

    /**
     * Merkt sich die Nachricht. Liefert einen Treffer, wenn sie im Zeitfenster schon vorkam, sonst null.
     */
    public synchronized Hit record(String raw, long now) {
        Parts p = split(raw);
        String key = keyOf(p);
        if (key == null) return null;
        if (now - lastPrune > 60_000) prune(now);
        Deque<Seen> q = seen.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst().at() > windowMs) q.pollFirst();
        Hit hit = q.isEmpty() ? null : new Hit(q.size() + 1, q.peekFirst().at(), q.peekLast().at(), q.peekFirst().sender());
        q.addLast(new Seen(now, p.sender()));
        if (q.size() > 50) q.pollFirst();
        return hit;
    }

    private void prune(long now) {
        lastPrune = now;
        Iterator<Map.Entry<String, Deque<Seen>>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Seen> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst().at() > windowMs) q.pollFirst();
            if (q.isEmpty()) it.remove();
        }
    }

    public synchronized void clear() {
        seen.clear();
    }
}
