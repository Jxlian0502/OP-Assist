package dev.opsuchtprices.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opsuchtprices.util.ModLog;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/**
 * Automatische Updates über GitHub-Releases: prüft beim Start die neueste Release, lädt die .jar als
 * "*.jar.download" in den mods-Ordner und installiert sie beim Beenden von Minecraft (über {@link Swapper}).
 */
public final class UpdateChecker {

    /** Ergebnis einer Prüfung. */
    public record Release(String version, String jarName, String downloadUrl, String sha256) {}

    private final HttpClient http;
    private final String repo;
    private final String currentVersion;
    private final Path currentJar;
    private volatile Path pendingDownload;
    private volatile Path pendingTarget;
    private volatile String pendingVersion;

    public UpdateChecker(HttpClient http, String repo, String currentVersion, Path currentJar) {
        this.http = http;
        this.repo = repo;
        this.currentVersion = currentVersion;
        this.currentJar = currentJar;
    }

    /** 1.16.0 < 1.16.1 < 1.17.0; ignoriert Präfix "v" und Zusätze. */
    public static int compare(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("[^0-9]+"), y = b.replaceFirst("^[vV]", "").split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    /** Liest die neueste Release aus der GitHub-API-Antwort; null, wenn keine passende .jar dabei ist. */
    public static Release parseRelease(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (o.has("draft") && o.get("draft").getAsBoolean()) return null;
        if (o.has("prerelease") && o.get("prerelease").getAsBoolean()) return null;
        String tag = o.has("tag_name") ? o.get("tag_name").getAsString() : null;
        if (tag == null) return null;
        JsonArray assets = o.has("assets") ? o.getAsJsonArray("assets") : new JsonArray();
        for (JsonElement e : assets) {
            JsonObject a = e.getAsJsonObject();
            String name = a.get("name").getAsString();
            if ((name.startsWith("op-assistent-") || name.startsWith("opsuchtprices-")) && name.endsWith(".jar") && !name.contains("sources")) {
                String sha = null;
                if (a.has("digest") && !a.get("digest").isJsonNull()) {
                    String d = a.get("digest").getAsString();
                    if (d.startsWith("sha256:")) sha = d.substring(7);
                }
                return new Release(tag.replaceFirst("^[vV]", ""), name, a.get("browser_download_url").getAsString(), sha);
            }
        }
        return null;
    }

    /**
     * Liest dist/latest.json aus dem Repo: {"version":"1.19.1","file":"op-assistent-1.19.1.jar","sha256":"…"}.
     * Die .jar liegt daneben in dist/. Liefert null bei ungültigem Inhalt.
     */
    public static Release parseLatestJson(String json, String rawBase) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (!o.has("version") || !o.has("file")) return null;
        String file = o.get("file").getAsString();
        if (!file.matches("[A-Za-z0-9._-]+\\.jar")) return null;
        String sha = o.has("sha256") && !o.get("sha256").isJsonNull() ? o.get("sha256").getAsString() : null;
        return new Release(o.get("version").getAsString().replaceFirst("^[vV]", ""), file, rawBase + "/dist/" + file, sha);
    }

    /** Prüft und lädt im Hintergrund. notify bekommt eine Meldung für den Chat (oder nichts). */
    public void checkAsync(Consumer<String> notify) {
        Thread t = new Thread(() -> {
            try {
                check(notify);
            } catch (Exception e) {
                ModLog.warn("Update-Prüfung fehlgeschlagen: {}", e.toString());
            }
        }, "opsuchtprices-update");
        t.setDaemon(true);
        t.start();
    }

    private void check(Consumer<String> notify) throws Exception {
        Path dir = currentJar.getParent();
        cleanupOldDownloads(dir);
        Release r = null;
        // 1) dist/latest.json im Repo (öffentlich über raw.githubusercontent.com, kein Token nötig)
        String rawBase = "https://raw.githubusercontent.com/" + repo + "/main";
        HttpResponse<String> latest = http.send(HttpRequest.newBuilder(URI.create(rawBase + "/dist/latest.json"))
                .timeout(Duration.ofSeconds(20)).header("User-Agent", "opsuchtprices-updater").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (latest.statusCode() == 200) {
            r = parseLatestJson(latest.body(), rawBase);
        } else {
            // 2) Ersatz: neueste GitHub-Release
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                            URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "opsuchtprices-updater")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) r = parseRelease(resp.body());
            else ModLog.info("Keine Update-Info in {} gefunden (HTTP {} / {})", repo, latest.statusCode(), resp.statusCode());
        }
        if (r == null || compare(r.version(), currentVersion) <= 0) {
            ModLog.info("Mod ist aktuell ({})", currentVersion);
            return;
        }
        Path target = dir.resolve(r.jarName());
        Path download = dir.resolve(r.jarName() + ".download");
        Path tmp = dir.resolve(r.jarName() + ".part");
        HttpResponse<InputStream> dl = http.send(HttpRequest.newBuilder(URI.create(r.downloadUrl()))
                .timeout(Duration.ofMinutes(2)).header("User-Agent", "opsuchtprices-updater").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (dl.statusCode() != 200) throw new java.io.IOException("Download HTTP " + dl.statusCode());
        try (InputStream in = dl.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        verify(tmp, r);
        Files.move(tmp, download, StandardCopyOption.REPLACE_EXISTING);
        pendingDownload = download;
        pendingTarget = target;
        pendingVersion = r.version();
        ModLog.info("Update {} heruntergeladen, wird beim Beenden installiert", r.version());
        notify.accept("Update " + r.version() + " heruntergeladen – wird beim Beenden von Minecraft installiert (aktuell: "
                + currentVersion + ").");
    }

    /** Prüft SHA-256 (falls GitHub sie liefert) und dass es wirklich diese Mod in der erwarteten Version ist. */
    public static void verify(Path jar, Release r) throws Exception {
        if (r.sha256() != null) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String actual = HexFormat.of().formatHex(md.digest(Files.readAllBytes(jar)));
            if (!actual.equalsIgnoreCase(r.sha256())) {
                Files.deleteIfExists(jar);
                throw new SecurityException("Prüfsumme des Updates stimmt nicht");
            }
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new SecurityException("Keine Fabric-Mod");
            JsonObject meta = JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"opsuchtprices".equals(meta.get("id").getAsString())) throw new SecurityException("Falsche Mod-ID");
            if (compare(meta.get("version").getAsString(), r.version()) != 0) throw new SecurityException("Version passt nicht");
        } catch (Exception e) {
            Files.deleteIfExists(jar);
            throw e;
        }
    }

    private void cleanupOldDownloads(Path dir) {
        try (var files = Files.list(dir)) {
            files.filter(p -> isOurFile(p.getFileName().toString())
                            && (p.toString().endsWith(".jar.download") || p.toString().endsWith(".jar.part")))
                    .forEach(p -> {
                        String v = p.getFileName().toString().replaceFirst("^(op-assistent|opsuchtprices)-", "").replaceAll("\\.jar.*$", "");
                        if (p.toString().endsWith(".part") || compare(v, currentVersion) <= 0) {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private static boolean isOurFile(String name) {
        return name.startsWith("op-assistent-") || name.startsWith("opsuchtprices-");
    }

    public String pendingVersion() {
        return pendingVersion;
    }

    /**
     * Beim Beenden: startet den Swapper als eigenen Prozess (aus einer Temp-Kopie der Klasse, damit keine .jar
     * gesperrt wird). Liegt eine ältere Vormerkung vom letzten Start vor, wird auch die installiert.
     */
    public void installOnExit(Path logFile) {
        Path download = pendingDownload, target = pendingTarget;
        if (download == null) {
            // Download aus einer früheren Sitzung?
            try (var files = Files.list(currentJar.getParent())) {
                var found = files.filter(p -> isOurFile(p.getFileName().toString())
                        && p.toString().endsWith(".jar.download")).findFirst();
                if (found.isEmpty()) return;
                download = found.get();
                String n = download.getFileName().toString();
                target = download.resolveSibling(n.substring(0, n.length() - ".download".length()));
            } catch (Exception e) {
                return;
            }
        }
        try {
            Path tmp = Files.createTempDirectory("opsuchtprices-updater");
            Path cls = tmp.resolve("dev/opsuchtprices/update/Swapper.class");
            Files.createDirectories(cls.getParent());
            try (InputStream in = UpdateChecker.class.getResourceAsStream("/dev/opsuchtprices/update/Swapper.class")) {
                if (in == null) return;
                Files.copy(in, cls, StandardCopyOption.REPLACE_EXISTING);
            }
            String java = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(java, "-cp", tmp.toString(), "dev.opsuchtprices.update.Swapper",
                    currentJar.toString(), download.toString(), target.toString(), logFile.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            ModLog.info("Update-Installation gestartet: {} -> {}", currentJar.getFileName(), target.getFileName());
        } catch (Exception e) {
            ModLog.warn("Update konnte nicht installiert werden: {}", e.toString());
        }
    }
}
