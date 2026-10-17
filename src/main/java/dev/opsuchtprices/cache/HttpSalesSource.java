package dev.opsuchtprices.cache;

import dev.opsuchtprices.api.PriceApiJson;
import dev.opsuchtprices.model.SaleRecord;
import dev.opsuchtprices.util.ModLog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Einzige Quelle der AH-Verkäufe: holt sie von der Preis-Schnittstelle des AH-Sammler-Servers
 * (der alle Verkäufe in der Datenbank sammelt) in den SalesStore im Arbeitsspeicher.
 * Kein Passwort nötig, funktioniert auf jedem PC. Tooltip und Liste lesen nur aus dem Speicher,
 * dadurch bremst das Netzwerk nie das Spiel. Lokal wird nichts gespeichert.
 *
 * Beim Start und alle 30 Minuten: die neuesten Verkäufe je Item komplett; dazwischen alle
 * refreshSeconds nur neue Verkäufe.
 */
public final class HttpSalesSource {
    public static final String USER_AGENT = "OP-Assistent (Fabric client mod)";
    private static final long FULL_RELOAD_MS = 30 * 60_000L;
    /** Überlappung beim Nachholen; doppelte Verkäufe erkennt der SalesStore an der uid. */
    private static final long OVERLAP_MS = 2 * 60_000L;

    private final SalesStore store;
    private final HttpClient http;
    private final String baseUrl;
    private final int maxPerItem;
    private final int refreshSeconds;
    private ScheduledExecutorService scheduler;
    private volatile String state = "startet";
    private volatile Instant newestSoldAt;
    private long lastFullLoad;

    public HttpSalesSource(SalesStore store, HttpClient http, String baseUrl, int maxPerItem, int refreshSeconds) {
        this.store = store;
        this.http = http;
        String b = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        this.maxPerItem = Math.max(1, Math.min(50, maxPerItem));
        this.refreshSeconds = Math.max(15, refreshSeconds);
    }

    /** Kurzer Status für /opa. */
    public String status() {
        return state;
    }

    public void start() {
        if (baseUrl.isEmpty()) {
            state = "aus (priceServerUrl leer)";
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OpSuchtPrices-Preisserver");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::tick, 0, refreshSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    /** Ein Durchlauf (öffentlich für Tests). */
    public void tick() {
        try {
            long now = System.currentTimeMillis();
            if (newestSoldAt == null || now - lastFullLoad > FULL_RELOAD_MS) {
                List<SaleRecord> all = fetch("/v1/sales/latest?n=" + maxPerItem).sales();
                store.replaceAll(all);
                lastFullLoad = now;
                newestSoldAt = newest(all, newestSoldAt == null ? Instant.ofEpochMilli(now - OVERLAP_MS) : newestSoldAt);
                ModLog.info("AH-Preise vom Preis-Server geladen: {} Verkäufe, {} Items", store.totalSales(), store.itemCount());
            } else {
                List<SaleRecord> fresh = fetch("/v1/sales/since?t=" + newestSoldAt.minusMillis(OVERLAP_MS).toEpochMilli()).sales();
                int added = 0;
                for (SaleRecord r : fresh) if (store.record(r)) added++;
                newestSoldAt = newest(fresh, newestSoldAt);
                if (added > 0) ModLog.debug("{} neue AH-Verkäufe vom Preis-Server", added);
            }
            state = "verbunden, " + store.totalSales() + " Verkäufe (" + store.itemCount() + " Items)";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            state = "nicht erreichbar (" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + shorten(e.getMessage())) + ")"
                    + (store.totalSales() > 0 ? ", zeige zuletzt geladene Preise" : "");
            ModLog.warn("AH-Preise konnten nicht vom Preis-Server geladen werden: {}", e.toString());
        }
    }

    private PriceApiJson.Result fetch(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
        byte[] body = resp.body();
        if (resp.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip")) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                body = in.readAllBytes();
            }
        }
        return PriceApiJson.parse(new String(body, StandardCharsets.UTF_8));
    }

    private static Instant newest(List<SaleRecord> list, Instant current) {
        Instant n = current;
        for (SaleRecord r : list) if (n == null || r.soldAt().isAfter(n)) n = r.soldAt();
        return n;
    }

    private static String shorten(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }
}
