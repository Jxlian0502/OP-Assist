package dev.opsuchtprices.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.opsuchtprices.util.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lädt Bilder von Custom Items (items.opsucht.net) als Minecraft-Texturen.
 *
 * - Download im Hintergrund, nie im Render-Thread.
 * - Zwischenspeicher auf der Festplatte (config/opsuchtprices/icons/), damit jedes
 *   Bild nur einmal geladen wird.
 * - Nur HTTPS-Bilder von items.opsucht.net, höchstens 512 KB und 256×256 Pixel.
 */
public final class RemoteIcons {

    private enum State { LOADING, FAILED }

    private static final String ALLOWED_HOST = "items.opsucht.net";
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_SIZE = 256;
    private static final long RETRY_AFTER_MS = 10 * 60 * 1000;

    private final HttpClient http;
    private final Path cacheDir;
    private final ExecutorService worker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "OpSuchtPrices-Icons");
        t.setDaemon(true);
        return t;
    });

    /** URL -> fertige Textur. */
    private final Map<String, Identifier> ready = new ConcurrentHashMap<>();
    /** URL -> Ladezustand (nur solange nicht fertig). */
    private final Map<String, State> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();

    public RemoteIcons(HttpClient http, Path cacheDir) {
        this.http = http;
        this.cacheDir = cacheDir;
    }

    /** true, wenn die URL ein Custom-Item-Bild ist, das hier geladen werden darf. */
    public static boolean isCustomIcon(String url) {
        if (url == null) return false;
        try {
            URI u = URI.create(url);
            return "https".equalsIgnoreCase(u.getScheme())
                    && ALLOWED_HOST.equalsIgnoreCase(u.getHost())
                    && u.getPath() != null && u.getPath().toLowerCase(Locale.ROOT).endsWith(".png");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Liefert die Textur, falls schon geladen. Sonst null und startet (einmalig) das Laden.
     * Darf aus dem Render-Thread aufgerufen werden, blockiert nie.
     */
    public Identifier get(String url) {
        Identifier id = ready.get(url);
        if (id != null) return id;
        if (!isCustomIcon(url)) return null;
        State s = pending.get(url);
        if (s == State.LOADING) return null;
        if (s == State.FAILED) {
            Long at = failedAt.get(url);
            if (at != null && System.currentTimeMillis() - at < RETRY_AFTER_MS) return null;
        }
        if (pending.put(url, State.LOADING) == State.LOADING) return null;
        worker.execute(() -> load(url));
        return null;
    }

    private void load(String url) {
        String hash = sha1(url);
        Path file = cacheDir.resolve(hash + ".png");
        try {
            byte[] bytes = Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
            if (bytes == null) {
                bytes = download(url);
                Files.createDirectories(cacheDir);
                Path tmp = Files.createTempFile(cacheDir, hash, ".tmp");
                try {
                    Files.write(tmp, bytes);
                    Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            NativeImage image = NativeImage.read(bytes);
            if (image.getWidth() > MAX_SIZE || image.getHeight() > MAX_SIZE || image.getWidth() < 1) {
                image.close();
                throw new IOException("Bild zu groß: " + image.getWidth() + "x" + image.getHeight());
            }
            Identifier id = Identifier.fromNamespaceAndPath(OpSuchtPricesClient.MOD_ID, "icons/" + hash);
            // Textur muss im Render-Thread hochgeladen werden
            Minecraft.getInstance().execute(() -> {
                try {
                    Minecraft.getInstance().getTextureManager()
                            .register(id, new DynamicTexture(() -> "opsuchtprices icon " + hash, image));
                    ready.put(url, id);
                    pending.remove(url);
                } catch (RuntimeException e) {
                    image.close();
                    fail(url, e);
                }
            });
        } catch (IOException | RuntimeException e) {
            // Kaputte Datei im Cache nicht wiederverwenden
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // egal
            }
            fail(url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(url);
        }
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", dev.opsuchtprices.api.AuctionStreamClient.USER_AGENT)
                .GET().build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
        byte[] body = resp.body();
        if (body == null || body.length == 0 || body.length > MAX_BYTES) {
            throw new IOException("ungültige Bildgröße: " + (body == null ? 0 : body.length) + " Bytes");
        }
        return body;
    }

    private void fail(String url, Exception e) {
        pending.put(url, State.FAILED);
        failedAt.put(url, System.currentTimeMillis());
        ModLog.debug("Bild {} konnte nicht geladen werden: {}", url, e.toString());
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Für die Statusausgabe. */
    public int loadedCount() {
        return ready.size();
    }
}
