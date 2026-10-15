package dev.opsuchtprices.cache;

import dev.opsuchtprices.api.AuctionStreamClient;
import dev.opsuchtprices.api.AuctionStreamEvent;
import dev.opsuchtprices.model.SaleRecord;
import dev.opsuchtprices.price.SaleDetector;
import dev.opsuchtprices.util.ModLog;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Schneidet AH-Verkäufe aus dem Live-Stream mit und speichert sie lokal.
 * Ein Hintergrund-Thread startet/stoppt den Stream je nach Server und
 * sichert neue Verkäufe alle 5 Sekunden in sales.json.
 */
public final class SaleTracker {

    private final SalesStore sales;
    private final Path salesFile;
    private final Clock clock;
    private final BooleanSupplier isActive;
    private final Function<Consumer<AuctionStreamEvent>, AuctionStreamClient> streamFactory;
    private ScheduledExecutorService scheduler;
    /** Liefert die Antwort von /auctions/active (für das Nachladen von Bild-URLs). */
    private volatile java.util.concurrent.Callable<String> activeAuctionsFetcher;
    private final java.util.concurrent.atomic.AtomicBoolean backfillRunning = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile java.time.Instant lastBackfill = java.time.Instant.EPOCH;
    private volatile AuctionStreamClient stream;

    public SaleTracker(SalesStore sales, Path salesFile, Clock clock, BooleanSupplier isActive,
                       Function<Consumer<AuctionStreamEvent>, AuctionStreamClient> streamFactory) {
        this.sales = sales;
        this.salesFile = salesFile;
        this.clock = clock;
        this.isActive = isActive;
        this.streamFactory = streamFactory;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OpSuchtPrices-Worker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::tick, 2, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        AuctionStreamClient s = stream;
        if (s != null) s.stop();
        if (scheduler != null) scheduler.shutdownNow();
        sales.saveIfDirty(salesFile);
    }

    /** Ein Durchlauf: Stream verwalten, Verkäufe sichern. Öffentlich für Tests. */
    public void tick() {
        try {
            boolean want = isActive.getAsBoolean();
            if (want && stream == null && streamFactory != null) {
                stream = streamFactory.apply(this::onStreamEvent);
                stream.start();
            } else if (!want && stream != null) {
                stream.stop();
                stream = null;
            }
            sales.saveIfDirty(salesFile);
        } catch (RuntimeException e) {
            ModLog.warn("Fehler im Hintergrund-Thread: {}", e.toString());
        }
    }

    public void setActiveAuctionsFetcher(java.util.concurrent.Callable<String> fetcher) {
        this.activeAuctionsFetcher = fetcher;
    }

    /**
     * Holt für gespeicherte Items ohne Bild die Bild-URL aus den aktuellen Auktionen.
     * Läuft im Hintergrund, höchstens alle 10 Minuten.
     */
    public void requestIconBackfill() {
        if (scheduler == null || activeAuctionsFetcher == null || sales.keysWithoutIcon().isEmpty()) return;
        if (java.time.Duration.between(lastBackfill, clock.instant()).toMinutes() < 10) return;
        if (!backfillRunning.compareAndSet(false, true)) return;
        lastBackfill = clock.instant();
        scheduler.execute(() -> {
            try {
                backfillIcons(activeAuctionsFetcher.call());
            } catch (Exception e) {
                ModLog.warn("Bilder konnten nicht nachgeladen werden: {}", e.toString());
            } finally {
                backfillRunning.set(false);
            }
        });
    }

    /** Ordnet Bild-URLs aus einer /auctions/active-Antwort den gespeicherten Items zu. Öffentlich für Tests. */
    public int backfillIcons(String activeAuctionsJson) {
        java.util.Set<String> missing = sales.keysWithoutIcon();
        java.util.Map<String, String> found = new java.util.HashMap<>();
        for (dev.opsuchtprices.model.Auction a : dev.opsuchtprices.api.AuctionParser.parseList(activeAuctionsJson)) {
            if (a.item().icon() == null) continue;
            String key = dev.opsuchtprices.matching.ItemMatcher.keyFor(
                    dev.opsuchtprices.matching.ItemSignature.of(a.item())).asString();
            if (missing.contains(key)) found.putIfAbsent(key, a.item().icon());
        }
        int n = sales.applyIcons(found);
        if (n > 0) {
            sales.saveIfDirty(salesFile);
            ModLog.info("Bilder für {} gespeicherte Items nachgetragen", n);
        }
        return n;
    }

    /** Verarbeitet ein Stream-Ereignis. Nur echte Verkäufe werden gespeichert. */
    public void onStreamEvent(AuctionStreamEvent ev) {
        SaleRecord sale = SaleDetector.detect(ev, clock.instant());
        if (sale != null && sales.record(sale)) {
            ModLog.debug("Verkauf erfasst: {} für {}", sale.itemKey(), sale.totalPrice());
        }
    }

    public SalesStore sales() {
        return sales;
    }

    public Clock clock() {
        return clock;
    }

    /** Kurzer Status für den Befehl /opa. */
    public String status() {
        AuctionStreamClient s = stream;
        String streamInfo = s == null ? "aus (nicht auf OP SUCHT)"
                : s.state() + ", " + s.eventsReceived() + " Ereignisse"
                + (s.lastError() == null ? "" : ", letzter Fehler: " + s.lastError());
        return "Live-Stream: " + streamInfo + " | Gespeicherte Verkäufe: " + sales.totalSales()
                + " (" + sales.itemCount() + " Items) | Liste: /opa liste";
    }
}
