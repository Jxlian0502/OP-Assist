package dev.opsuchtprices.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

/**
 * Eigenständiges Hilfsprogramm (ohne Minecraft-Abhängigkeiten), das nach dem Beenden von Minecraft läuft:
 * wartet, bis die alte .jar nicht mehr gesperrt ist (Windows), löscht sie und benennt die heruntergeladene
 * Datei in die neue .jar um. Aufruf: java -cp <tempdir> dev.opsuchtprices.update.Swapper <alt.jar> <neu.jar.download> <neu.jar> <log>
 */
public final class Swapper {
    private Swapper() {}

    public static void main(String[] args) throws Exception {
        Path oldJar = Path.of(args[0]), download = Path.of(args[1]), target = Path.of(args[2]);
        Path log = args.length > 3 ? Path.of(args[3]) : null;
        long deadline = System.currentTimeMillis() + 5 * 60_000L; // bis zu 5 Min. auf das Spielende warten
        while (true) {
            try {
                if (!Files.exists(download)) {
                    log(log, "Download fehlt, nichts zu tun");
                    return;
                }
                if (!oldJar.equals(target)) Files.deleteIfExists(oldJar);
                Files.move(download, target, StandardCopyOption.REPLACE_EXISTING);
                log(log, "Update installiert: " + target.getFileName() + " (alt: " + oldJar.getFileName() + ")");
                return;
            } catch (IOException e) {
                if (System.currentTimeMillis() > deadline) {
                    log(log, "Update nicht möglich (" + e + "), alte Version bleibt");
                    return;
                }
                Thread.sleep(1000);
            }
        }
    }

    private static void log(Path log, String msg) {
        if (log == null) return;
        try {
            Files.writeString(log, LocalDateTime.now() + " " + msg + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
