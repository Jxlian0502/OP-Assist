package dev.opsuchtprices.market;

import dev.opsuchtprices.util.ModLog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Lädt und cached Marktdaten. Alle HTTP-Anfragen laufen in einem eigenen
 * Hintergrund-Thread; das GUI liest nur fertige, unveränderliche Snapshots.
 *
 * - Preise werden höchstens alle {@code refreshInterval} neu geladen (manuell frühestens alle 5 s).
 * - Historien werden pro Item bei Bedarf geladen, 10 Minuten gecacht, höchstens eine Anfrage pro Sekunde.
 * - Bei Fehlern bleiben die zuletzt geladenen Daten erhalten.
 */
public final class MarketService {

    /** Unveränderlicher Datenstand. */
    public record Snapshot(List<MarketEntry> entries, MarketStats stats, Instant loadedAt) {}

    /** Holt den Text einer URL (blockierend, nur im Hintergrund aufgerufen). */
    public interface Fetcher {
        String get(String url) throws Exception;
    }

    private static final Duration MANUAL_COOLDOWN = Duration.ofSeconds(5);
    private static final Duration HISTORY_TTL = Duration.ofMinutes(10);
    private static final Duration HISTORY_SPACING = Duration.ofSeconds(1);

    private final Fetcher fetcher;
    private final String pricesUrl;
    private final Function<String, String> historyUrl;
    private final Clock clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OpSuchtPrices-Markt");
        t.setDaemon(true);
        return t;
    });

    private volatile Duration refreshInterval;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile Instant lastAttempt = Instant.EPOCH;
    private volatile String lastError;
    private final Map<String, MarketHistory> history = new ConcurrentHashMap<>();
    private final Map<String, Boolean> historyLoading = new ConcurrentHashMap<>();
    private volatile Instant lastHistoryRequest = Instant.EPOCH;

    public MarketService(Fetcher fetcher, String pricesUrl, Function<String, String> historyUrl,
                         Duration refreshInterval, Clock clock) {
        this.fetcher = fetcher;
        this.pricesUrl = pricesUrl;
        this.historyUrl = historyUrl;
        this.refreshInterval = refreshInterval;
        this.clock = clock;
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    public boolean isLoading() {
        return loading.get();
    }

    public String lastError() {
        return lastError;
    }

    /** Lädt neu, wenn noch keine Daten da sind oder sie älter als das Intervall sind. */
    public void refreshIfStale() {
        Snapshot s = snapshot.get();
        Instant now = clock.instant();
        boolean stale = s == null || Duration.between(s.loadedAt(), now).compareTo(refreshInterval) >= 0;
        // nach einem Fehler nicht ständig neu versuchen
        boolean backoff = lastError != null && Duration.between(lastAttempt, now).compareTo(Duration.ofSeconds(30)) < 0;
        if (stale && !backoff) startLoad();
    }

    /** Manuelle Aktualisierung (Knopf). Liefert false, wenn gerade erst geladen wurde. */
    public boolean refreshNow() {
        if (Duration.between(lastAttempt, clock.instant()).compareTo(MANUAL_COOLDOWN) < 0) return false;
        return startLoad();
    }

    private boolean startLoad() {
        if (!loading.compareAndSet(false, true)) return false;
        lastAttempt = clock.instant();
        worker.execute(() -> {
            try {
                loadNow();
            } finally {
                loading.set(false);
            }
        });
        return true;
    }

    /** Synchroner Abruf. Öffentlich für Tests. */
    public void loadNow() {
        try {
            List<MarketEntry> entries = MarketParser.parsePrices(fetcher.get(pricesUrl));
            if (entries.isEmpty()) throw new java.io.IOException("Die Markt-API hat keine Items geliefert");
            snapshot.set(new Snapshot(List.copyOf(entries), MarketStats.of(entries, 5), clock.instant()));
            lastError = null;
            ModLog.debug("Markt geladen: {} Items", entries.size());
        } catch (Exception e) {
            lastError = message(e);
            ModLog.warn("Marktdaten konnten nicht geladen werden: {}", e.toString());
        }
    }

    /** Historie eines Items, falls geladen; stößt sonst (gedrosselt) das Laden an. Blockiert nie. */
    public MarketHistory history(String material) {
        String m = material.toUpperCase(Locale.ROOT);
        MarketHistory h = history.get(m);
        Instant now = clock.instant();
        boolean fresh = h != null && Duration.between(h.fetchedAt(), now).compareTo(HISTORY_TTL) < 0;
        if (!fresh && !historyLoading.containsKey(m)
                && Duration.between(lastHistoryRequest, now).compareTo(HISTORY_SPACING) >= 0) {
            lastHistoryRequest = now;
            historyLoading.put(m, Boolean.TRUE);
            worker.execute(() -> {
                try {
                    history.put(m, MarketParser.parseHistory(fetcher.get(historyUrl.apply(m)), clock.instant()));
                } catch (Exception e) {
                    ModLog.debug("Historie für {} nicht ladbar: {}", m, e.toString());
                } finally {
                    historyLoading.remove(m);
                }
            });
        }
        return h;
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    private static String message(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) return "Zeitüberschreitung beim Laden";
        if (e instanceof java.net.ConnectException) return "Markt-API nicht erreichbar";
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
