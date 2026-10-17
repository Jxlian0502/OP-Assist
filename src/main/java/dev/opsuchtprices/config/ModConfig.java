package dev.opsuchtprices.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.opsuchtprices.util.AtomicFiles;
import dev.opsuchtprices.util.ModLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Konfiguration in config/opsuchtprices/config.json. */
public final class ModConfig {

    public boolean enabled = true;
    /** Die API nennt keine Währung, daher frei einstellbar. */
    public String currencyLabel = "$";
    /** Nur auf OP SUCHT aktiv (Serveradresse enthält einen der Einträge). */
    public boolean onlyOnOpSucht = true;
    public List<String> serverAddressContains = new ArrayList<>(List.of("opsucht"));
    /** Adresse des Auktions-Streams (Server-Sent Events). */
    public String streamUrl = "https://api.opsucht.net/auctions/stream";
    /** Aktuelle Auktionen, um Bilder für ältere Einträge nachzuladen. */
    public String activeAuctionsUrl = "https://api.opsucht.net/auctions/active";
    /** Bilder von Custom Items anzeigen (werden von items.opsucht.net geladen). */
    public boolean showCustomIcons = true;
    /** Befehl für die Marktübersicht (ohne "/"). Nicht "markt", das ist der Server-Befehl von OP SUCHT. */
    public String marketCommand = "marktinfo";
    /** Eigene Oberfläche über dem /markt-Menü des Servers (Kaufen/Verkaufen über das Overlay). */
    public boolean marketOverlay = true;
    /** Basis-URL der Markt-API. */
    public String marketApiUrl = "https://api.opsucht.net/market";
    /** Marktpreise werden höchstens so oft neu geladen (Sekunden). */
    public int marketRefreshSeconds = 60;
    /** So viele Verkäufe pro Item werden gespeichert (Basis für den Durchschnitt). */
    public int maxSalesPerItem = 50;
    /**
     * Preis-Server des AH-Sammlers: einzige Quelle der AH-Verkäufe für Tooltip und Liste.
     * Funktioniert ohne Zugangsdaten auf jedem PC.
     */
    public String priceServerUrl = "http://217.160.209.120";
    /** So oft werden neue Verkäufe vom Preis-Server geholt (Sekunden). */
    public int priceRefreshSeconds = 60;
    public boolean debugLogging = false;
    /** Automatische Updates über GitHub-Releases. */
    public boolean autoUpdate = true;
    /** GitHub-Repository "besitzer/name" mit den Releases. */
    public String updateRepo = "Jxlian0502/OP-Assist";
    /** Jobtracker: liest die Job-Actionbar (XP, Geld, Level) und zeigt ein HUD. */
    public boolean jobTracker = true;
    /** HUD-Position in GUI-Pixeln (von links/oben). */
    public int jobHudX = 4;
    public int jobHudY = 4;
    /** Nach so vielen Sekunden ohne Job-Meldung pausiert die Zeitmessung. */
    public int jobAutoPauseSeconds = 60;
    /** HUD nach so vielen Minuten ohne Job-Meldung ausblenden (0 = nie). */
    public int jobHideAfterMinutes = 5;
    /** Job-Actionbar des Servers ausblenden (das HUD zeigt dieselben Werte). */
    public boolean jobHideActionBar = false;
    /** HUD ausblenden, solange ein Menü offen ist (Markt-Overlay, Inventar, Truhen; nicht im Chat). */
    public boolean jobHideInMenus = true;
    /** HUD ausblenden, solange die Tabliste offen ist (Tab gedrückt). */
    public boolean jobHideWithTabList = true;
    /** XP-Zähler: zählt bis zu diesem Wert, dann beginnt er wieder bei 0 (0 = Zähler aus). */
    public int jobXpCounterStep = 100_000;
    /** Shard-Wert des Inventars im Jobtracker anzeigen (Kurse aus der API). */
    public boolean jobShowShards = true;
    /** Chatscanner: markiert Nachrichten, die derselbe Spieler im Zeitfenster schon einmal geschrieben hat. */
    public boolean chatScanner = true;
    /** Zeitfenster in Minuten. */
    public int chatScannerMinutes = 15;
    /** Kürzere Nachrichten (Zeichen) werden ignoriert, z. B. "hi". */
    public int chatScannerMinLength = 5;
    /** Server-Meldungen ("OPSUCHT » …") nicht markieren. */
    public boolean chatScannerSkipServer = true;
    /** Wiederholte Nachrichten zusätzlich rot einfärben (wirkt auch mit LabyMod, das den Chat selbst zeichnet). */
    public boolean chatScannerRedText = true;
    /** Händler-Kurse (Item -> Shards/Redcoins). */
    public String merchantRatesUrl = "https://api.opsucht.net/merchant/rates";
    /** Kurse höchstens so oft neu laden (Minuten). */
    public int shardRefreshMinutes = 5;
    /** Version der Config-Datei, für automatische Anpassungen bei Updates. */
    public int configVersion = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public ModConfig sanitize() {
        maxSalesPerItem = Math.max(1, Math.min(maxSalesPerItem, 1000));
        if (priceServerUrl == null) priceServerUrl = "";
        priceRefreshSeconds = Math.max(15, Math.min(priceRefreshSeconds, 3600));
        // Alte Standardwährung aus Version 1.1.0 automatisch auf $ umstellen
        if (currencyLabel == null || currencyLabel.equalsIgnoreCase("Coins")) currencyLabel = "$";
        if (streamUrl == null || streamUrl.isBlank()) streamUrl = "https://api.opsucht.net/auctions/stream";
        if (activeAuctionsUrl == null || activeAuctionsUrl.isBlank()) activeAuctionsUrl = "https://api.opsucht.net/auctions/active";
        if (marketCommand == null || !marketCommand.trim().matches("[a-zA-Z0-9_-]{1,32}")) marketCommand = "marktinfo";
        marketCommand = marketCommand.trim().toLowerCase(java.util.Locale.ROOT);
        if (marketApiUrl == null || marketApiUrl.isBlank()) marketApiUrl = "https://api.opsucht.net/market";
        marketApiUrl = marketApiUrl.trim().replaceAll("/+$", "");
        marketRefreshSeconds = Math.max(15, Math.min(marketRefreshSeconds, 3600));
        jobAutoPauseSeconds = Math.max(1, Math.min(jobAutoPauseSeconds, 3600));
        jobHideAfterMinutes = Math.max(0, Math.min(jobHideAfterMinutes, 1440));
        jobXpCounterStep = Math.max(0, jobXpCounterStep);
        // Platzhalter aus älteren Versionen durch das echte Update-Repo ersetzen
        if (updateRepo == null || updateRepo.isBlank() || updateRepo.startsWith("OWNER/")) updateRepo = "Jxlian0502/OP-Assist";
        chatScannerMinutes = Math.max(1, Math.min(chatScannerMinutes, 1440));
        chatScannerMinLength = Math.max(1, Math.min(chatScannerMinLength, 200));
        if (merchantRatesUrl == null || merchantRatesUrl.isBlank()) merchantRatesUrl = "https://api.opsucht.net/merchant/rates";
        shardRefreshMinutes = Math.max(1, Math.min(shardRefreshMinutes, 1440));
        jobHudX = Math.max(0, jobHudX);
        jobHudY = Math.max(0, jobHudY);
        if (serverAddressContains == null) serverAddressContains = new ArrayList<>(List.of("opsucht"));
        return this;
    }

    /** Lädt die Config. Fehlt sie oder ist sie kaputt, werden Standardwerte geschrieben. */
    public static ModConfig load(Path file) {
        ModConfig cfg = null;
        try {
            String json = AtomicFiles.readStringOrNull(file);
            if (json != null) cfg = GSON.fromJson(json, ModConfig.class);
        } catch (IOException | JsonParseException e) {
            ModLog.warn("Config {} unlesbar, verwende Standardwerte: {}", file, e.toString());
        }
        if (cfg == null) cfg = new ModConfig();
        if (cfg.configVersion < 2) {
            // Version 1.1.0 hatte "Coins" als Standard, OP SUCHT nutzt "$"
            if ("Coins".equals(cfg.currencyLabel)) cfg.currencyLabel = "$";
            cfg.configVersion = 2;
        }
        cfg.sanitize();
        try {
            AtomicFiles.writeString(file, GSON.toJson(cfg));
        } catch (IOException e) {
            ModLog.warn("Config {} konnte nicht gespeichert werden: {}", file, e.toString());
        }
        return cfg;
    }

    /** Speichert die aktuellen Werte (z. B. nach Änderung per Befehl). */
    public void save(Path file) {
        try {
            AtomicFiles.writeString(file, GSON.toJson(sanitize()));
        } catch (IOException e) {
            ModLog.warn("Config {} konnte nicht gespeichert werden: {}", file, e.toString());
        }
    }
}
