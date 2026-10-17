package dev.opsuchtprices.gametest;

import dev.opsuchtprices.client.MarketOverlay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Automatischer Test des Markt-Overlays: öffnet nachgebaute Menüs aus einer echten
 * Aufnahme, klickt im Overlay und prüft, welcher Slot-Klick an den Server ginge
 * (im Test abgefangen, es wird nichts gesendet).
 */
public class MarketOverlayGameTest implements FabricClientGameTest {

    public static final List<String> CLICKS = new CopyOnWriteArrayList<>();
    private static final Path MENUS = extract();

    /** Testdaten (echte Aufnahme) aus den Ressourcen in eine Datei kopieren. */
    private static Path extract() {
        try (var in = MarketOverlayGameTest.class.getResourceAsStream("/markt-test.txt")) {
            Path p = java.nio.file.Files.createTempFile("markt-test", ".txt");
            java.nio.file.Files.write(p, in.readAllBytes());
            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3); // wie ein typischer Bildschirm (ergibt ca. 426x240 GUI-Pixel = kleinster Fall)
            mc.resizeDisplay();
        });
        ctx.waitTicks(3);
        // 1) Übersicht
        open(ctx, 0);
        ctx.takeScreenshot("overlay-1-uebersicht");
        hover(ctx, "Diamantblock");
        ctx.takeScreenshot("overlay-2-hover");
        expectClick(ctx, "Diamantblock", "14:0", "Kachel Diamantblock -> Linksklick Slot 14");
        expectClick(ctx, "Weiter", "52:0", "Weiter -> Slot 52");
        expectClick(ctx, "Nahrung", "27:0", "Kategorie Nahrung -> Slot 27");
        expectClick(ctx, "Suche", "50:0", "Suche -> Slot 50");

        // 2) Item-Seite (Sand, 1 im Inventar)
        open(ctx, 6);
        ctx.takeScreenshot("overlay-3-item");
        click(ctx, "1 kaufen");
        if (!CLICKS.isEmpty()) throw new AssertionError("1 kaufen darf erst nach Bestätigung klicken: " + CLICKS);
        ctx.takeScreenshot("overlay-4-bestaetigen");
        expectClick(ctx, "Sicher?", "10:0", "1 kaufen bestätigt -> Linksklick Slot 10");
        expectClick(ctx, "Kaufen: Anzahl", "10:1", "Kaufen Anzahl -> Rechtsklick Slot 10");
        click(ctx, "1 verkaufen");
        expectClick(ctx, "Sicher?", "11:0", "1 verkaufen bestätigt -> Linksklick Slot 11");
        expectClick(ctx, "Verkauf: Anzahl", "11:1", "Verkaufen Anzahl -> Rechtsklick Slot 11");
        expectClick(ctx, "Kaufauftrag", "15:0", "Kaufauftrag -> Slot 15");
        expectClick(ctx, "Verkaufsangebot", "16:0", "Verkaufsangebot (1 Sand im Inventar) -> Slot 16");
        expectClick(ctx, "◀ Zurück", "27:0", "Zurück -> Slot 27");

        // Item-Seite ohne Items im Inventar: Verkaufen gesperrt
        open(ctx, 5);
        ctx.waitTicks(2);
        if (ctx.computeOnClient(mc -> MarketOverlay.findTarget("1 verkaufen")) != null
                || ctx.computeOnClient(mc -> MarketOverlay.findTarget("Verkauf: Anzahl")) != null
                || ctx.computeOnClient(mc -> MarketOverlay.findTarget("Verkaufsangebot")) != null) {
            throw new AssertionError("Verkaufen ohne Items muss gesperrt sein");
        }
        if (ctx.computeOnClient(mc -> MarketOverlay.findTarget("1 kaufen")) == null) {
            throw new AssertionError("Kaufen muss möglich sein");
        }
        ctx.takeScreenshot("overlay-5-ohne-items");
        System.out.println("OVERLAY-TEST ok: Verkaufen ohne Items gesperrt");

        // 3) Kaufauftrag: Menge -> Preis -> Bestätigen
        open(ctx, 8);
        ctx.takeScreenshot("overlay-8-auftrag-menge");
        expectClick(ctx, "1x Sand", "11:0", "Menge 1x -> Slot 11");
        expectClick(ctx, "Eigene Menge", "31:0", "Eigene Menge -> Slot 31 (Amboss des Servers)");
        open(ctx, 9);
        ctx.takeScreenshot("overlay-9-auftrag-preis");
        expectClick(ctx, "Bestes Angebot +", "13:0", "Preis Bestes Angebot +0.1 -> Slot 13");
        expectClick(ctx, "5% der Spanne", "15:0", "Preis 5% der Spanne -> Slot 15");
        open(ctx, 10);
        ctx.takeScreenshot("overlay-10-auftrag-bestaetigen");
        expectClick(ctx, "Bestätigen", "13:0", "Bestätigen -> genau 1 Klick auf Slot 13");
        expectClick(ctx, "◀ Zurück", "27:0", "Zurück -> Slot 27");

        // 4) Deine Aufträge + Auftrag abbrechen
        open(ctx, 22);
        ctx.takeScreenshot("overlay-11-auftraege");
        if (ctx.computeOnClient(mc -> MarketOverlay.findTarget("Alle einsammeln")) != null) {
            throw new AssertionError("Ohne einsammelbare Aufträge muss 'Alle einsammeln' gesperrt sein");
        }
        expectClick(ctx, "Auftrag Sand", "28:0", "Auftrag anklicken -> Slot 28");
        expectClick(ctx, "Filter:", "53:0", "Filter -> Slot 53");
        expectClick(ctx, "◀ Zurück", "45:0", "Aufträge Zurück -> Slot 45");
        // Alle einsammeln: erster Klick sofort, weitere erst nach Antwort des Servers (bleibt hier aus -> Stopp)
        open(ctx, 43);
        ctx.takeScreenshot("overlay-13-einsammelbar");
        click(ctx, "Alle einsammeln (3)");
        ctx.waitTicks(10);
        if (!CLICKS.equals(List.of("28:0"))) throw new AssertionError("Einsammeln: erwartet [28:0], war " + CLICKS);
        if (!ctx.computeOnClient(mc -> MarketOverlay.isCollecting())) throw new AssertionError("Einsammeln sollte warten");
        ctx.waitTicks(60);
        if (ctx.computeOnClient(mc -> MarketOverlay.isCollecting())) throw new AssertionError("Ohne Server-Antwort muss gestoppt werden");
        if (!CLICKS.equals(List.of("28:0"))) throw new AssertionError("Ohne Antwort kein weiterer Klick, war " + CLICKS);
        ctx.takeScreenshot("overlay-14-einsammeln-gestoppt");
        System.out.println("OVERLAY-TEST ok: Alle einsammeln klickt einzeln und stoppt ohne Server-Antwort");
        // Antwort simulieren: Inhalt ändert sich -> nächster Klick
        CLICKS.clear();
        click(ctx, "Alle einsammeln (3)");
        ctx.waitTicks(4);
        ctx.runOnClient(mc -> ((net.minecraft.client.gui.screens.inventory.ContainerScreen) mc.screen).getMenu()
                .getSlot(28).set(net.minecraft.world.item.ItemStack.EMPTY));
        ctx.waitTicks(20);
        if (!CLICKS.equals(List.of("28:0", "29:0"))) throw new AssertionError("Nach Antwort nächster Auftrag erwartet, war " + CLICKS);
        System.out.println("OVERLAY-TEST ok: nach Server-Antwort folgt der nächste Auftrag");
        ctx.waitTicks(70);

        open(ctx, 23);
        ctx.takeScreenshot("overlay-12-auftrag-abbrechen");
        expectClick(ctx, "Auftrag abbrechen", "13:0", "Auftrag abbrechen -> 1 Klick auf Slot 13");
        expectClick(ctx, "◀ Zurück", "18:0", "Abbrechen-Seite Zurück -> Slot 18");

        // Scrollen in der Übersicht: alle 24 Items erreichbar
        open(ctx, 0);
        hover(ctx, "Netheritbarren");
        for (int k = 0; k < 20; k++) ctx.getInput().scroll(-1);
        ctx.waitTicks(3);
        ctx.takeScreenshot("overlay-7-gescrollt");
        expectClick(ctx, "Netherquarz", "43:0", "letztes Item (Netherquarz) nach Scrollen -> Slot 43");

        // 4) Umschalten auf Original-Menü
        open(ctx, 0);
        click(ctx, "Original-Menü");
        ctx.waitTicks(3);
        ctx.takeScreenshot("overlay-6-original");
        if (ctx.computeOnClient(mc -> MarketOverlay.isActive((ContainerScreen) mc.screen))) {
            throw new AssertionError("Original-Menü wurde nicht angezeigt");
        }
        // Jobtracker-HUD mit nachgestellter Actionbar (Format aus dem Screenshot)
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1_000_000);
        dev.opsuchtprices.job.JobTracker jt = new dev.opsuchtprices.job.JobTracker(clock::get, 60_000);
        for (int i = 0; i < 200; i++) {
            clock.addAndGet(4500);
            String pct = String.format(java.util.Locale.GERMANY, "%.2f", 40 + i * 0.0845);
            jt.onActionBar("§e+2,75 XP §8• §a+15,887$ §8• §bHolzfäller §7Level 78 §8• §8[§a||||||§7||||§8] §7" + pct + "%");
        }
        clock.addAndGet(1000);
        if (jt.current() == null || jt.current().actions != 200) throw new AssertionError("Jobtracker zählt falsch");
        dev.opsuchtprices.client.JobHud.init(jt, null, () -> true);
        dev.opsuchtprices.job.XpCounter xc = new dev.opsuchtprices.job.XpCounter(null, 100_000);
        xc.add(345_231);
        xc.treasureFound(1);
        xc.add(1_102);
        for (int i = 0; i < 1234; i++) xc.addTree();
        dev.opsuchtprices.client.JobHud.setCounter(xc);
        dev.opsuchtprices.client.ShardValue.setForTest(java.util.List.of(), java.util.Map.of("opshards", 1255.32));
        // Echter Weg: Actionbar über Gui#setOverlayMessage (wie beim Actionbar-Paket des Servers)
        ctx.runOnClient(mc -> mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(
                "§e+2,75 XP §8• §a+15,887$ §8• §bHolzfäller §7Level 78 §8• §8[§a|||§7|||§8] §764,85%"), false));
        if (jt.current().actions != 201 || Math.abs(jt.current().percent - 64.85) > 1e-9) {
            throw new AssertionError("Actionbar-Paket wurde nicht gezählt: " + jt.current().actions);
        }
        System.out.println("OVERLAY-TEST ok: Actionbar über setOverlayMessage erkannt");
        ctx.runOnClient(mc -> mc.setScreen(new JobHudTestScreen()));
        ctx.waitTicks(5);
        ctx.takeScreenshot("jobtracker-hud");
        System.out.println("OVERLAY-TEST ok: Jobtracker-HUD");

        // Chatscanner: gleiche Nachricht vom gleichen Spieler wird markiert (rot hinterlegt), von anderem nicht
        java.util.List<String> shown = ctx.computeOnClient(mc -> {
            dev.opsuchtprices.client.ChatScanner.init(new dev.opsuchtprices.config.ModConfig(), () -> true);
            mc.gui.getChat().clearMessages(false);
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String m : new String[]{
                    "§7[VIP] §bMaxMustermann §8» §fVerkaufe Diamantblöcke für 700$ /msg me",
                    "§7Spieler §eTim §8» §fhat jemand Netherite?",
                    "§7Spieler §eTim §8» §fVerkaufe Diamantblöcke für 700$ /msg me",
                    "§7[VIP] §bMaxMustermann §8» §fVerkaufe Diamantblöcke für 700$ /msg me",
                    "§7Spieler §eTim §8» §fich kauf Sand",
                    "§7[VIP] §bMaxMustermann §8» §fverkaufe diamantblöcke für 700$  /msg me"}) {
                net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.literal(m);
                if (dev.opsuchtprices.client.ChatScanner.allowGame(c, false)) mc.gui.getChat().addMessage(c);
                else out.add(m);
            }
            return out;
        });
        if (shown.size() != 2 || shown.get(0).contains("Tim")) throw new AssertionError("Chatscanner falsch: " + shown);
        // Rote Schrift (für LabyMod & Co.): jeder Textteil der markierten Nachricht ist rot
        Integer col = ctx.computeOnClient(mc -> {
            var d = dev.opsuchtprices.client.ChatScanner.decorate(net.minecraft.network.chat.Component.literal("§bMax §8» §fhallo welt"),
                    new dev.opsuchtprices.chat.ChatRepeatTracker.Hit(2, 0, 0, "Max"));
            int[] c = {-1};
            d.visit((style, text) -> {
                if (text.contains("hallo")) c[0] = style.getColor() == null ? -1 : style.getColor().getValue();
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            return c[0];
        });
        if (col == null || col != 0xFF5555) throw new AssertionError("Text nicht rot: " + Integer.toHexString(col == null ? -1 : col));
        ctx.runOnClient(mc -> mc.setScreen(new ChatTestScreen(java.util.List.of())));
        ctx.waitTicks(5);
        ctx.takeScreenshot("chatscanner");
        System.out.println("OVERLAY-TEST ok: Chatscanner markiert nur gleichen Spieler");

        // Einstellungen: Schieberegler + Freitext, Drag & Drop
        dev.opsuchtprices.config.ModConfig cfg = new dev.opsuchtprices.config.ModConfig();
        dev.opsuchtprices.client.JobHud.init(jt, cfg, () -> true);
        ctx.runOnClient(mc -> {
            var ls = new dev.opsuchtprices.job.LifetimeStats(null);
            ls.migrateTrees(31);
            for (int i = 0; i < 12; i++) ls.addDiamondOre();
            ls.addSpawner();
            dev.opsuchtprices.client.StatsTracker.init(ls);
            dev.opsuchtprices.client.StatsTracker.onChat("OPSUCHT » Verbinde zu FARMWELT 1...");
            mc.setScreen(new dev.opsuchtprices.client.SettingsScreen(null, cfg, () -> {}));
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> ((dev.opsuchtprices.client.SettingsScreen) mc.screen).setPauseText("1:30"));
        ctx.waitTicks(3);
        if (cfg.jobAutoPauseSeconds != 90) throw new AssertionError("Freitext 1:30 nicht übernommen: " + cfg.jobAutoPauseSeconds);
        ctx.takeScreenshot("einstellungen");
        System.out.println("OVERLAY-TEST ok: Einstellungen Pausezeit 1:30 -> 90 s");
        ctx.runOnClient(mc -> mc.setScreen(new dev.opsuchtprices.client.JobHudMoveScreen(null, cfg, () -> {})));
        ctx.waitTicks(5);
        // echtes Ziehen mit der Maus: Panel bei (4,4) greifen und nach (150,60) ziehen
        double sc = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(20 * sc, 10 * sc);
        ctx.waitTicks(2);
        ctx.getInput().holdMouse(0);
        ctx.waitTicks(2);
        ctx.getInput().setCursorPos(90 * sc, 40 * sc);
        ctx.waitTicks(2);
        ctx.getInput().setCursorPos(166 * sc, 66 * sc);
        ctx.waitTicks(2);
        ctx.getInput().releaseMouse(0);
        ctx.waitTicks(3);
        ctx.takeScreenshot("jobtracker-verschieben");
        ctx.runOnClient(mc -> mc.screen.onClose());
        ctx.waitTicks(3);
        if (Math.abs(cfg.jobHudX - 150) > 2 || Math.abs(cfg.jobHudY - 60) > 2) {
            throw new AssertionError("Drag & Drop falsch: " + cfg.jobHudX + "," + cfg.jobHudY);
        }
        System.out.println("OVERLAY-TEST ok: Drag & Drop -> " + cfg.jobHudX + "," + cfg.jobHudY);

        // HUD überdeckt weder Menüs (Markt-Overlay, Inventar …) noch die Tabliste; im Chat bleibt es sichtbar
        String hudErr = ctx.computeOnClient(mc -> {
            var chat = new net.minecraft.client.gui.screens.ChatScreen("", false);
            var menu = new JobHudTestScreen();
            if (dev.opsuchtprices.client.JobHud.hiddenByOverlay(null, false, cfg)) return "HUD ohne Menü ausgeblendet";
            if (!dev.opsuchtprices.client.JobHud.hiddenByOverlay(null, true, cfg)) return "HUD bei offener Tabliste sichtbar";
            if (dev.opsuchtprices.client.JobHud.hiddenByOverlay(chat, false, cfg)) return "HUD im Chat ausgeblendet";
            if (!dev.opsuchtprices.client.JobHud.hiddenByOverlay(menu, false, cfg)) return "HUD über offenem Menü sichtbar";
            cfg.jobHideInMenus = false;
            cfg.jobHideWithTabList = false;
            boolean ignored = dev.opsuchtprices.client.JobHud.hiddenByOverlay(menu, true, cfg);
            cfg.jobHideInMenus = true;
            cfg.jobHideWithTabList = true;
            return ignored ? "Optionen jobHideInMenus/jobHideWithTabList werden ignoriert" : null;
        });
        if (hudErr != null) throw new AssertionError(hudErr);
        System.out.println("OVERLAY-TEST ok: HUD aus bei Menü und Tabliste, an im Chat");

        // AH-Preise vom echten Preis-Server (Standard-Adresse aus der Config), wie auf jedem PC ohne Einrichtung
        String[] priceState = ctx.computeOnClient(mc -> {
            var store = new dev.opsuchtprices.cache.SalesStore(50);
            var src = new dev.opsuchtprices.cache.HttpSalesSource(store, java.net.http.HttpClient.newHttpClient(),
                    new dev.opsuchtprices.config.ModConfig().priceServerUrl, 50, 60);
            src.tick(); // komplett laden
            src.tick(); // neue Verkäufe nachholen
            String sample = store.overview().isEmpty() ? "-" : store.overview().get(0).label() + " = "
                    + store.overview().get(0).summary().last().totalPrice();
            return new String[]{src.status(), String.valueOf(store.totalSales()), sample};
        });
        System.out.println("OVERLAY-TEST info: Preis-Server -> " + priceState[0] + " | neuester: " + priceState[2]);
        if (!priceState[0].startsWith("verbunden") || Integer.parseInt(priceState[1]) < 1000) {
            throw new AssertionError("Preis-Server liefert keine Preise: " + priceState[0]);
        }
        // Unerreichbarer Server: kein Absturz, verständlicher Status
        String offline = ctx.computeOnClient(mc -> {
            var src = new dev.opsuchtprices.cache.HttpSalesSource(new dev.opsuchtprices.cache.SalesStore(50),
                    java.net.http.HttpClient.newHttpClient(), "http://127.0.0.1:1", 50, 60);
            src.tick();
            return src.status();
        });
        if (!offline.startsWith("nicht erreichbar")) throw new AssertionError("Offline-Status falsch: " + offline);
        System.out.println("OVERLAY-TEST ok: AH-Preise vom Preis-Server (" + priceState[1] + " Verkäufe), offline ohne Absturz");

        // Jobtracker-Anzeige: "Zeit bis Level-Up" ausschalten -> Zeile weg, Panel kürzer
        ctx.runOnClient(mc -> mc.setScreen(new dev.opsuchtprices.client.JobHudDisplayScreen(null, cfg, () -> {})));
        ctx.waitTicks(3);
        ctx.clickScreenButton("Zeit bis Level-Up: AN");
        ctx.waitTicks(2);
        ctx.takeScreenshot("jobtracker-anzeige-einstellungen");
        if (cfg.jobShowLevelUp) throw new AssertionError("Level-Up nicht ausgeschaltet");
        cfg.jobShowLevelUp = true;
        ctx.runOnClient(mc -> mc.setScreen(new JobHudTestScreen()));
        ctx.waitTicks(3);
        int fullH = ctx.computeOnClient(mc -> dev.opsuchtprices.client.JobHud.lastPanelHeight());
        cfg.jobShowLevelUp = false;
        cfg.jobShowProgress = false;
        ctx.waitTicks(3);
        int shortH = ctx.computeOnClient(mc -> dev.opsuchtprices.client.JobHud.lastPanelHeight());
        ctx.takeScreenshot("jobtracker-ohne-levelup-und-balken");
        if (shortH != fullH - 20) throw new AssertionError("Panel-Höhe falsch: " + fullH + " -> " + shortH);
        cfg.jobShowLevelUp = true;
        cfg.jobShowProgress = true;
        System.out.println("OVERLAY-TEST ok: Jobtracker-Zeilen abschaltbar (Höhe " + fullH + " -> " + shortH + ")");

        // Job-Geld-Limit: echter Wortlaut vom Server -> eine Info, Wiederholungen ohne Spam
        String[] limit = ctx.computeOnClient(mc -> {
            long now = System.currentTimeMillis();
            String msg = "§4OPSUCHT §8» §cDu hast dein Job-Geld-Limit erreicht, weiter geht es in 22 Minuten!";
            String first = dev.opsuchtprices.client.OpSuchtPricesClient.onMoneyLimitChat(msg, now);
            String again = dev.opsuchtprices.client.OpSuchtPricesClient.onMoneyLimitChat(msg.replace("22", "21"), now + 60_000);
            String other = dev.opsuchtprices.client.OpSuchtPricesClient.onMoneyLimitChat("Du hast einen Jobschatz erhalten!", now);
            return new String[]{first, again, other};
        });
        System.out.println("OVERLAY-TEST info: Limit-Info -> " + limit[0]);
        if (limit[0] == null || !limit[0].contains("Job-Geld-Limit erreicht") || !limit[0].contains("1,5 Mio")
                || !limit[0].contains("Uhr (in 22 Min.)") || limit[1] != null || limit[2] != null) {
            throw new AssertionError("Job-Geld-Limit-Info falsch: " + java.util.Arrays.toString(limit));
        }
        System.out.println("OVERLAY-TEST ok: Job-Geld-Limit-Info einmal, ohne Spam");

        // Einblendung in einer echten Welt ansehen (Titel wie "JOBSCHATZ GEFUNDEN")
        try (var sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            // wie im echten Spiel: Jobtracker oben links (Standard), Chat ohne die Testnachrichten von oben
            cfg.jobHudX = 4;
            cfg.jobHudY = 4;
            ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
            ctx.runOnClient(mc -> dev.opsuchtprices.client.OpSuchtPricesClient.onMoneyLimitChat(
                    "§4OPSUCHT §8» §cDu hast dein Job-Geld-Limit erreicht, weiter geht es in 45 Minuten!",
                    System.currentTimeMillis()));
            ctx.waitTicks(25);
            ctx.takeScreenshot("job-geld-limit-titel");
            String sub = dev.opsuchtprices.client.OpSuchtPricesClient.lastLimitSubtitle;
            if (sub == null || !sub.startsWith("Wieder Geld ab") || !sub.contains("(in 45 Min.)")) {
                throw new AssertionError("Untertitel falsch: " + sub);
            }
            System.out.println("OVERLAY-TEST ok: Job-Geld-Limit als Titel eingeblendet (" + sub + ")");

            // Inventar: Knopfleiste + Speed-Anzeige. Speed-Items: Stiefel +50 %, Axt in der Hand +30 % -> +80 %
            sp.getServer().runCommand("item replace entity @p armor.feet with minecraft:leather_boots[minecraft:attribute_modifiers="
                    + "[{type:\"minecraft:movement_speed\",amount:0.5,operation:\"add_multiplied_base\",slot:\"feet\",id:\"test:boots\"}]]");
            sp.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:iron_axe[minecraft:attribute_modifiers="
                    + "[{type:\"minecraft:movement_speed\",amount:0.3,operation:\"add_multiplied_base\",slot:\"mainhand\",id:\"test:axe\"}]]");
            sp.getServer().runCommand("effect give @p minecraft:speed 120 1 true"); // Schnelligkeit II: +40 %
            ctx.waitTicks(10);
            boolean[] saved = {false};
            dev.opsuchtprices.client.InventoryButtons.init(cfg, () -> true, () -> saved[0] = true);
            ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            ctx.waitTicks(10);
            String speed = ctx.computeOnClient(mc -> dev.opsuchtprices.util.SpeedMath.format(
                    dev.opsuchtprices.client.InventoryButtons.speedPercent(mc.player)));
            if (!"+120%".equals(speed)) throw new AssertionError("Speed falsch (erwartet Items +80 % + Schnelligkeit II +40 %): " + speed);
            double gs = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            int[] b0 = ctx.computeOnClient(mc -> dev.opsuchtprices.client.InventoryButtons.buttonCenter(mc.screen, 0));
            ctx.getInput().setCursorPos(b0[0] * gs, b0[1] * gs);
            ctx.waitTicks(3);
            ctx.takeScreenshot("inventar-knoepfe");
            // Klick auf "Bank" -> /bank
            dev.opsuchtprices.client.InventoryButtons.lastCommand = null;
            ctx.getInput().pressMouse(0);
            ctx.waitTicks(3);
            if (!"bank".equals(dev.opsuchtprices.client.InventoryButtons.lastCommand)) {
                throw new AssertionError("Klick auf Bank-Knopf: " + dev.opsuchtprices.client.InventoryButtons.lastCommand);
            }
            System.out.println("OVERLAY-TEST ok: Inventar-Knopf Bank -> /bank, Speed " + speed);
            // Leiste per Drag & Drop verschieben: kein Befehl, Position gespeichert
            ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            ctx.waitTicks(5);
            dev.opsuchtprices.client.InventoryButtons.lastCommand = null;
            int ox = cfg.inventoryButtonsX + 2 * 22, oy = cfg.inventoryButtonsY; // Standardplatz von Knopf 3
            int[] b1Before = ctx.computeOnClient(mc -> dev.opsuchtprices.client.InventoryButtons.buttonCenter(mc.screen, 1));
            int[] b2 = ctx.computeOnClient(mc -> dev.opsuchtprices.client.InventoryButtons.buttonCenter(mc.screen, 2));
            ctx.getInput().setCursorPos(b2[0] * gs, b2[1] * gs);
            ctx.waitTicks(2);
            ctx.getInput().holdMouse(0);
            ctx.waitTicks(2);
            ctx.getInput().setCursorPos((b2[0] + 20) * gs, (b2[1] + 60) * gs);
            ctx.waitTicks(2);
            ctx.getInput().setCursorPos((b2[0] + 40) * gs, (b2[1] + 125) * gs);
            ctx.waitTicks(2);
            ctx.getInput().releaseMouse(0);
            ctx.waitTicks(3);
            ctx.getInput().setCursorPos(5, 5);
            ctx.waitTicks(2);
            ctx.takeScreenshot("inventar-knoepfe-verschoben");
            var moved = cfg.inventoryButtonList.get(2);
            int[] b1After = ctx.computeOnClient(mc -> dev.opsuchtprices.client.InventoryButtons.buttonCenter(mc.screen, 1));
            if (dev.opsuchtprices.client.InventoryButtons.lastCommand != null || !saved[0] || moved.x == null
                    || Math.abs(moved.x - ox - 40) > 2 || Math.abs(moved.y - oy - 125) > 2
                    || !java.util.Arrays.equals(b1Before, b1After) || cfg.inventoryButtonList.get(1).x != null) {
                throw new AssertionError("Verschieben falsch: Knopf3=" + moved.x + "," + moved.y + " Knopf2 vorher/nachher="
                        + java.util.Arrays.toString(b1Before) + "/" + java.util.Arrays.toString(b1After)
                        + " Befehl=" + dev.opsuchtprices.client.InventoryButtons.lastCommand + " gespeichert=" + saved[0]);
            }
            System.out.println("OVERLAY-TEST ok: einzelner Inventar-Knopf per Drag & Drop verschoben (" + ox + "," + oy + " -> "
                    + moved.x + "," + moved.y + "), andere Knöpfe unverändert, kein Befehl ausgelöst");
            // /opa speed: Diagnose zeigt die Testitems
            java.util.List<String> diag = ctx.computeOnClient(mc -> dev.opsuchtprices.client.SpeedDiagnosis.lines(mc.player));
            String diagText = String.join("\n", diag);
            if (!diagText.contains("Speed-Anzeige: +120% (Items +80%, Schnelligkeit Stufe 2 x1 = +40%)") || !diagText.contains("Attribut minecraft:movement_speed")) {
                throw new AssertionError("Speed-Diagnose unvollständig:\n" + diagText);
            }
            System.out.println("OVERLAY-TEST ok: /opa speed Diagnose (" + diag.size() + " Zeilen)");
            // Nur im eigenen Inventar, nicht in anderen Menüs (z. B. Werkbank/Truhe)
            ctx.runOnClient(mc -> mc.setScreen(null));
            ctx.waitTicks(2);
        }

        ctx.setScreen(() -> new net.minecraft.client.gui.screens.TitleScreen());
        System.out.println("OVERLAY-TEST OK");
    }

    private static void open(ClientGameTestContext ctx, int index) {
        ctx.runOnClient(mc -> {
            try {
                Inventory inv = new Inventory(null, new EntityEquipment());
                mc.setScreen(DebugMarketMenu.open(MENUS.toAbsolutePath(), index, inv));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ctx.waitTicks(5);
        CLICKS.clear();
    }

    private static int[] pos(ClientGameTestContext ctx, String label) {
        ctx.waitTicks(2);
        int[] p = ctx.computeOnClient(mc -> MarketOverlay.findTarget(label));
        if (p == null) throw new AssertionError("Knopf nicht gefunden: " + label);
        return p;
    }

    private static void hover(ClientGameTestContext ctx, String label) {
        int[] p = pos(ctx, label);
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(p[0] * scale + 0.5, p[1] * scale + 0.5);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, String label) {
        hover(ctx, label);
        ctx.getInput().pressMouse(0);
        ctx.waitTicks(6); // > 200 ms Doppelklick-Schutz
    }

    private static void expectClick(ClientGameTestContext ctx, String label, String expected, String what) {
        CLICKS.clear();
        click(ctx, label);
        if (!CLICKS.equals(List.of(expected))) {
            throw new AssertionError("Erwartet: " + what + " [" + expected + "], war: " + CLICKS);
        }
        System.out.println("OVERLAY-TEST ok: " + what);
    }
}
