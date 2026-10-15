package dev.opsuchtprices.api;

import dev.opsuchtprices.util.ModLog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Hält eine Server-Sent-Events-Verbindung zum Auktions-Stream offen und
 * verbindet sich bei Abbruch mit Backoff neu (inkl. Last-Event-ID).
 * Läuft in einem eigenen Daemon-Thread.
 */
public final class AuctionStreamClient {

    public enum State { STOPPED, CONNECTING, CONNECTED, WAITING, UNAVAILABLE }

    public static final String USER_AGENT = "OpSuchtPriceChecker/1.1 (Fabric client mod)";

    private final HttpClient client;
    private final String url;
    private final Consumer<AuctionStreamEvent> handler;
    private volatile boolean running;
    private volatile State state = State.STOPPED;
    private volatile String lastError = null;
    private volatile String lastEventId = null;
    private volatile long eventsReceived = 0;
    private volatile Stream<String> currentStream;
    private Thread thread;

    private static final long MIN_BACKOFF_MS = 5_000;
    private static final long MAX_BACKOFF_MS = 300_000;
    /** Bei 404 o. Ä. existiert der Stream evtl. nicht: selten neu versuchen. */
    private static final long UNAVAILABLE_RETRY_MS = 600_000;

    public AuctionStreamClient(HttpClient client, String url, Consumer<AuctionStreamEvent> handler) {
        this.client = client;
        this.url = url;
        this.handler = handler;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "OpSuchtPrices-Stream");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        state = State.STOPPED;
        Stream<String> s = currentStream;
        if (s != null) {
            try {
                s.close();
            } catch (RuntimeException ignored) {
                // egal, wir beenden ohnehin
            }
        }
        if (thread != null) thread.interrupt();
    }

    public State state() {
        return state;
    }

    public String lastError() {
        return lastError;
    }

    public long eventsReceived() {
        return eventsReceived;
    }

    private void loop() {
        long backoff = MIN_BACKOFF_MS;
        while (running) {
            long wait = backoff;
            try {
                state = State.CONNECTING;
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                        .header("Accept", "text/event-stream")
                        .header("User-Agent", USER_AGENT)
                        .GET();
                if (lastEventId != null) rb.header("Last-Event-ID", lastEventId);
                HttpResponse<Stream<String>> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofLines());
                int status = resp.statusCode();
                if (status == 200) {
                    state = State.CONNECTED;
                    lastError = null;
                    ModLog.info("Auktions-Stream verbunden");
                    backoff = MIN_BACKOFF_MS;
                    SseParser parser = new SseParser(this::onEvent);
                    try (Stream<String> lines = resp.body()) {
                        currentStream = lines;
                        Iterator<String> it = lines.iterator();
                        while (running && it.hasNext()) {
                            parser.accept(it.next());
                            if (parser.lastEventId() != null) lastEventId = parser.lastEventId();
                        }
                    } finally {
                        currentStream = null;
                    }
                    lastError = "Verbindung geschlossen";
                    wait = MIN_BACKOFF_MS;
                } else {
                    resp.body().close(); // Body verwerfen
                    lastError = "HTTP " + status;
                    if (status == 429) {
                        long ra = parseRetryAfter(resp.headers().firstValue("Retry-After").orElse(null));
                        wait = ra > 0 ? ra * 1000 : Math.max(backoff, 60_000);
                    } else if (status >= 400 && status < 500) {
                        state = State.UNAVAILABLE;
                        ModLog.warn("Auktions-Stream {} nicht verfügbar (HTTP {}). Neuer Versuch in 10 Minuten.", url, status);
                        wait = UNAVAILABLE_RETRY_MS;
                    }
                    backoff = Math.min(MAX_BACKOFF_MS, backoff * 2);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running) break;
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                ModLog.debug("Stream-Fehler: {}", lastError);
                backoff = Math.min(MAX_BACKOFF_MS, backoff * 2);
            }
            if (!running) break;
            if (state != State.UNAVAILABLE) state = State.WAITING;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        state = State.STOPPED;
    }

    private void onEvent(SseParser.Event ev) {
        eventsReceived++;
        AuctionStreamEvent decoded = AuctionStreamEvent.decode(ev);
        if (decoded == null) {
            ModLog.debug("Stream-Ereignis '{}' nicht verwertbar", ev.event());
            return;
        }
        try {
            handler.accept(decoded);
        } catch (RuntimeException e) {
            ModLog.warn("Fehler bei Verarbeitung von Stream-Ereignis {}: {}", ev.event(), e.toString());
        }
    }

    static long parseRetryAfter(String value) {
        if (value == null) return -1;
        try {
            return Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
