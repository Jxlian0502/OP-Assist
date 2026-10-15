package dev.opsuchtprices.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.opsuchtprices.api.AuctionStreamClient;
import dev.opsuchtprices.cache.SaleTracker;
import dev.opsuchtprices.cache.SalesStore;
import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.market.MarketService;
import dev.opsuchtprices.util.ModLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Einstiegspunkt der Client-Mod. */
public final class OpSuchtPricesClient implements ClientModInitializer {

    public static final String MOD_ID = "opsuchtprices";

    /** Wird im Client-Tick gesetzt, damit Hintergrund-Threads nicht auf Minecraft zugreifen. */
    private static volatile boolean onOpSucht = false;

    /** Wird im nächsten Client-Tick geöffnet (nach dem Schließen des Chats). */
    private static volatile Screen pendingScreen;
    private static final boolean DEBUG_SCREEN = Boolean.getBoolean("opsuchtprices.debugScreen");
    private static boolean debugShown = false;

    @Override
    public void onInitializeClient() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        ModConfig config = ModConfig.load(dir.resolve("config.json"));
        ModLog.setDebug(config.debugLogging);

        Path salesFile = dir.resolve("sales.json");
        SalesStore sales = SalesStore.load(salesFile, config.maxSalesPerItem);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        RemoteIcons remoteIcons = new RemoteIcons(http, dir.resolve("icons"));

        SaleTracker tracker = new SaleTracker(sales, salesFile, Clock.systemUTC(),
                () -> config.enabled && (!config.onlyOnOpSucht || onOpSucht),
                handler -> new AuctionStreamClient(http, config.streamUrl, handler));

        tracker.setActiveAuctionsFetcher(() -> {
            java.net.http.HttpResponse<String> resp = http.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(config.activeAuctionsUrl))
                            .timeout(Duration.ofSeconds(20))
                            .header("Accept", "application/json")
                            .header("User-Agent", AuctionStreamClient.USER_AGENT)
                            .GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new java.io.IOException("HTTP " + resp.statusCode());
            return resp.body();
        });

        MarketService market = new MarketService(url -> {
            java.net.http.HttpResponse<String> resp = http.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("Accept", "application/json")
                            .header("User-Agent", AuctionStreamClient.USER_AGENT)
                            .GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 429) throw new java.io.IOException("Zu viele Anfragen, bitte später erneut versuchen");
            if (resp.statusCode() != 200) throw new java.io.IOException("Markt-API antwortet mit HTTP " + resp.statusCode());
            return resp.body();
        }, config.marketApiUrl + "/prices",
                material -> config.marketApiUrl + "/history/" + java.net.URLEncoder.encode(material, java.nio.charset.StandardCharsets.UTF_8),
                Duration.ofSeconds(config.marketRefreshSeconds), Clock.systemUTC());

        MarketOverlay.init(market, config);

        // Automatische Updates (nur in der fertigen .jar, nicht in der Entwicklungsumgebung)
        dev.opsuchtprices.update.UpdateChecker updater = createUpdater(http, config);
        if (updater != null) {
            updater.checkAsync(msg -> Minecraft.getInstance().execute(() -> pendingUpdateMessage = msg));
        }
        ChatScanner.init(config, () -> !config.onlyOnOpSucht || onOpSucht);
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register(ChatScanner::allowGame);
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_CHAT.register(
                (message, signed, sender, params, receptionTimestamp) -> ChatScanner.allowChat(message));
        ShardValue.init(url -> {
            java.net.http.HttpResponse<String> resp = http.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("Accept", "application/json")
                            .header("User-Agent", AuctionStreamClient.USER_AGENT)
                            .GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new java.io.IOException("Händler-API antwortet mit HTTP " + resp.statusCode());
            return resp.body();
        }, config);

        // Jobtracker: liest die Job-Actionbar (XP, Geld, Level) und zeigt ein HUD
        dev.opsuchtprices.job.JobTracker jobs = new dev.opsuchtprices.job.JobTracker(
                System::currentTimeMillis, config.jobAutoPauseSeconds * 1000L);
        // Das Actionbar-Format ist eindeutig genug, daher unabhängig von der Server-Erkennung
        JobHud.init(jobs, config, () -> true);
        configRef = config;
        configFile = dir.resolve("config.json");
        jobsRef = jobs;
        dev.opsuchtprices.job.XpCounter xpCounter = dev.opsuchtprices.job.XpCounter.load(
                dir.resolve("job-zaehler.json"), config.jobXpCounterStep);
        JobHud.setCounter(xpCounter);
        // Dauerhafte Statistik (Bäume, Diamanterze, Spawner) – nur Farmwelt 1–12 / Nether 1–2
        dev.opsuchtprices.job.LifetimeStats lifetime = dev.opsuchtprices.job.LifetimeStats.load(dir.resolve("statistik.json"));
        lifetime.migrateTrees(xpCounter.trees());
        lifetime.saveIfDirty();
        StatsTracker.init(lifetime);
        net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state) -> StatsTracker.onBlockBroken(state));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) StatsTracker.onChat(message.getString());
        });
        // "Du hast einen Jobschatz erhalten!" -> Jobschatz-Zähler auf 0, Fundzähler +1 (bleibt immer erhalten)
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && dev.opsuchtprices.job.XpCounter.isTreasureMessage(message.getString())
                    && xpCounter.treasureFound(System.currentTimeMillis())) {
                xpCounter.saveIfDirty();
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("✔ Jobschatz Nr. " + xpCounter.found()
                                + " – Zähler beginnt wieder bei 0.").withStyle(ChatFormatting.AQUA), false);
                    }
                });
            }
            return true;
        });
        jobs.setXpListener(xp -> {
            if (config.jobXpCounterStep <= 0) return;
            int done = xpCounter.add(xp);
            if (done > 0) {
                Minecraft mc = Minecraft.getInstance();
                String step = dev.opsuchtprices.util.PriceFormat.number(xpCounter.step());
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("✔ Jobschatz: " + step + " XP erreicht! Zähler beginnt neu (" + xpCounter.rounds() + "× voll).")
                                .withStyle(ChatFormatting.AQUA), false);
                    }
                    mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f));
                });
                xpCounter.saveIfDirty();
            }
        });
        // Bäume zählen: Anschlagen eines Stamms merken (danach folgende Holzfäller-Meldungen = ein Baum)
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide() && world.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) {
                JobHud.TREES.onLogAttack(System.currentTimeMillis());
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        // Die Actionbar wird über GuiMixin -> JobHud.onActionBar gelesen (Actionbar-Paket und Overlay-Chat)
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "jobtracker"), JobHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            onOpSucht = isOpSuchtServer(client, config);
            // Nur für Tests: -Dopsuchtprices.autoQuitSeconds=N beendet Minecraft sauber nach N Sekunden
            if (AUTO_QUIT_MS > 0 && System.currentTimeMillis() - STARTED_AT > AUTO_QUIT_MS) client.stop();
            if (pendingUpdateMessage != null && client.player != null) {
                client.player.displayClientMessage(Component.literal("[OP-Assistent] " + pendingUpdateMessage)
                        .withStyle(ChatFormatting.GREEN), false);
                pendingUpdateMessage = null;
            }
            if (client.level != null && client.level.getGameTime() % 200 == 0) xpCounter.saveIfDirty();
            GuiRecorder.tick(client);
            MarketOverlay.tick(client);
            StatsTracker.tick(client);
            ShardValue.tick(client, config.jobTracker && client.player != null);
            // Nur für Entwicklung: -Dopsuchtprices.debugScreen=true öffnet die Übersicht im Hauptmenü
            if (DEBUG_SCREEN && !debugShown && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                debugShown = true;
                // Entwicklungstest: Klasse laden, damit der Mixin sofort angewendet (oder ein Fehler sichtbar) wird
                ModLog.info("Debug: Mixin-Ziel geladen, {} Methoden",
                        net.minecraft.client.multiplayer.MultiPlayerGameMode.class.getDeclaredMethods().length
                                + net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredMethods().length);
                pendingScreen = "markt".equals(System.getProperty("opsuchtprices.debugScreenType"))
                        ? new MarketScreen(market, config)
                        : new SalesScreen(tracker, config, remoteIcons, System.getProperty("opsuchtprices.debugFilter", ""));
            }
            Screen next = pendingScreen;
            if (next != null) {
                pendingScreen = null;
                client.setScreen(next);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> tracker.start());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (StatsTracker.stats() != null) StatsTracker.stats().saveIfDirty();
            if (updater != null) updater.installOnExit(dir.resolve("update.log"));
            xpCounter.saveIfDirty();
            tracker.stop();
            market.shutdown();
        });

        ItemTooltipCallback.EVENT.register(new PriceTooltipHandler(tracker, config,
                () -> !config.onlyOnOpSucht || onOpSucht));

        // /opa                    Status (Stream verbunden? Anzahl Verkäufe)
        // /opa liste            öffnet die Übersicht aller gespeicherten Items
        // /opa liste <text>  öffnet sie mit vorausgefüllter Suche
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("opa")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal(tracker.status()).withStyle(ChatFormatting.GRAY));
                            return 1;
                        })
                        .then(literal("aufnahme")
                                .then(literal("start").executes(ctx -> {
                                    Path target = dir.resolve("markt-aufnahme.txt");
                                    ServerData sd = ctx.getSource().getClient().getCurrentServer();
                                    GuiRecorder.start(target, sd == null ? "-" : sd.ip);
                                    ctx.getSource().sendFeedback(Component.literal(GuiRecorder.isRecording()
                                            ? "Aufnahme läuft. Klick dich jetzt normal durch /markt (Kategorie, Item, Kaufen, Verkaufen, Menge). Danach: /opa aufnahme stop"
                                            : "Aufnahme konnte nicht gestartet werden.").withStyle(ChatFormatting.YELLOW));
                                    return 1;
                                }))
                                .then(literal("stop").executes(ctx -> {
                                    int m = GuiRecorder.menus(), c = GuiRecorder.clicks();
                                    Path f = GuiRecorder.stop();
                                    if (f == null) {
                                        ctx.getSource().sendFeedback(Component.literal("Es läuft keine Aufnahme.").withStyle(ChatFormatting.GRAY));
                                        return 0;
                                    }
                                    ctx.getSource().sendFeedback(Component.literal("Aufnahme gespeichert (" + m + " Menüs, " + c + " Klicks): ")
                                            .withStyle(ChatFormatting.GREEN)
                                            .append(Component.literal("[Ordner öffnen]").withStyle(s -> s
                                                    .withColor(ChatFormatting.AQUA).withUnderlined(true)
                                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenFile(f.getParent())))));
                                    return 1;
                                })))
                        .then(literal("job")
                                .executes(ctx -> {
                                    var cur = jobs.current();
                                    String msg = cur == null ? "Jobtracker: noch keine Job-Meldung erkannt."
                                            : "Jobtracker: " + cur.job + " Level " + cur.level + ", " + cur.actions + " Belohnungen, +"
                                            + dev.opsuchtprices.util.PriceFormat.exact(cur.money, config.currencyLabel) + ", +"
                                            + dev.opsuchtprices.util.PriceFormat.exact(cur.xp, "") + " XP";
                                    ctx.getSource().sendFeedback(Component.literal(msg).withStyle(ChatFormatting.GRAY));
                                    return 1;
                                })
                                .then(literal("zaehler")
                                        .executes(ctx -> {
                                            ctx.getSource().sendFeedback(Component.literal("Jobschatz: "
                                                    + dev.opsuchtprices.util.PriceFormat.number(Math.floor(xpCounter.current())) + " / "
                                                    + dev.opsuchtprices.util.PriceFormat.number(xpCounter.step()) + " XP, "
                                                    + xpCounter.found() + "× gefunden, insgesamt "
                                                    + dev.opsuchtprices.util.PriceFormat.number(xpCounter.totalXp()) + " XP").withStyle(ChatFormatting.GRAY));
                                            return 1;
                                        })
                                        .then(literal("reset").executes(ctx -> {
                                            xpCounter.reset();
                                            xpCounter.saveIfDirty();
                                            ctx.getSource().sendFeedback(Component.literal("Jobschatz auf 0 gesetzt.").withStyle(ChatFormatting.GREEN));
                                            return 1;
                                        }))
                                        .then(argument("grenze", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(ctx -> {
                                            config.jobXpCounterStep = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "grenze");
                                            config.save(dir.resolve("config.json"));
                                            if (config.jobXpCounterStep > 0) xpCounter.setStep(config.jobXpCounterStep);
                                            ctx.getSource().sendFeedback(Component.literal(config.jobXpCounterStep == 0 ? "Jobschatz aus."
                                                    : "Jobschatz zählt jetzt bis " + dev.opsuchtprices.util.PriceFormat.number(config.jobXpCounterStep) + " XP.")
                                                    .withStyle(ChatFormatting.GREEN));
                                            return 1;
                                        })))
                                .then(literal("reset").executes(ctx -> {
                                    jobs.reset();
                                    ctx.getSource().sendFeedback(Component.literal("Jobtracker zurückgesetzt.").withStyle(ChatFormatting.GREEN));
                                    return 1;
                                }))
                                .then(literal("toggle").executes(ctx -> {
                                    config.jobTracker = !config.jobTracker;
                                    config.save(dir.resolve("config.json"));
                                    ctx.getSource().sendFeedback(Component.literal(config.jobTracker ? "Jobtracker an." : "Jobtracker aus.")
                                            .withStyle(config.jobTracker ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                                    return 1;
                                }))
                                .then(literal("actionbar")
                                        .then(literal("an").executes(ctx -> {
                                            config.jobHideActionBar = false;
                                            config.save(dir.resolve("config.json"));
                                            ctx.getSource().sendFeedback(Component.literal("Job-Actionbar wird wieder angezeigt.").withStyle(ChatFormatting.GREEN));
                                            return 1;
                                        }))
                                        .then(literal("aus").executes(ctx -> {
                                            config.jobHideActionBar = true;
                                            config.save(dir.resolve("config.json"));
                                            ctx.getSource().sendFeedback(Component.literal("Job-Actionbar ausgeblendet (Werte stehen im HUD).").withStyle(ChatFormatting.YELLOW));
                                            return 1;
                                        }))))
                        .then(literal("chat")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(Component.literal("Chatscanner ist " + (config.chatScanner ? "an" : "aus")
                                            + " (" + config.chatScannerMinutes + " Min., gleicher Spieler + gleicher Text).").withStyle(ChatFormatting.GRAY));
                                    return 1;
                                })
                                .then(literal("an").executes(ctx -> {
                                    config.chatScanner = true;
                                    config.save(dir.resolve("config.json"));
                                    ctx.getSource().sendFeedback(Component.literal("Chatscanner an.").withStyle(ChatFormatting.GREEN));
                                    return 1;
                                }))
                                .then(literal("aus").executes(ctx -> {
                                    config.chatScanner = false;
                                    config.save(dir.resolve("config.json"));
                                    ctx.getSource().sendFeedback(Component.literal("Chatscanner aus.").withStyle(ChatFormatting.YELLOW));
                                    return 1;
                                })))
                        .then(literal("einstellungen").executes(ctx -> {
                            pendingScreen = settingsScreen(null);
                            return 1;
                        }))
                        .then(literal("ordner").executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal("Datenordner: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal("[öffnen]").withStyle(s -> s
                                            .withColor(ChatFormatting.AQUA).withUnderlined(true)
                                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenFile(dir)))));
                            return 1;
                        }))
                        .then(literal("liste")
                                .executes(ctx -> {
                                    pendingScreen = new SalesScreen(tracker, config, remoteIcons, "");
                                    return 1;
                                })
                                .then(argument("suche", StringArgumentType.greedyString()).executes(ctx -> {
                                    pendingScreen = new SalesScreen(tracker, config, remoteIcons,
                                            StringArgumentType.getString(ctx, "suche"));
                                    return 1;
                                })))));

        // /marktinfo (Name in der Config änderbar): Übersicht über den OP SUCHT Markt
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal(config.marketCommand).executes(ctx -> {
                    pendingScreen = new MarketScreen(market, config);
                    return 1;
                })));

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(GuiRecorder::onServerMessage);
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(MarketOverlay::onServerMessage);

        ModLog.info("OP-Assistent (by Jxlian0502) geladen, Verkäufe werden in {} gespeichert", salesFile);
    }

    private static volatile String pendingUpdateMessage;
    private static final long STARTED_AT = System.currentTimeMillis();
    private static final long AUTO_QUIT_MS = Long.getLong("opsuchtprices.autoQuitSeconds", 0L) * 1000L;
    private static ModConfig configRef;
    private static Path configFile;
    private static dev.opsuchtprices.job.JobTracker jobsRef;

    /** Einstellungs-Bildschirm (Mod-Menü und /opa einstellungen). Speichert beim Schließen und übernimmt die Pausezeit. */
    public static Screen settingsScreen(Screen parent) {
        return new SettingsScreen(parent, configRef, () -> {
            configRef.save(configFile);
            if (jobsRef != null) jobsRef.setPauseMs(configRef.jobAutoPauseSeconds * 1000L);
        });
    }

    /** Updater nur, wenn die Mod aus einer .jar läuft und ein Repo eingestellt ist. */
    private static dev.opsuchtprices.update.UpdateChecker createUpdater(HttpClient http, ModConfig config) {
        if (!config.autoUpdate || config.updateRepo == null || !config.updateRepo.matches("[\\w.-]+/[\\w.-]+")
                || config.updateRepo.startsWith("OWNER/")) return null;
        var mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
        if (mod == null) return null;
        Path jar = null;
        for (Path p : mod.getOrigin().getPaths()) {
            if (p.getFileName() != null && p.getFileName().toString().endsWith(".jar") && java.nio.file.Files.isRegularFile(p)) jar = p;
        }
        if (jar == null) return null; // Entwicklungsumgebung
        return new dev.opsuchtprices.update.UpdateChecker(http, config.updateRepo,
                mod.getMetadata().getVersion().getFriendlyString(), jar);
    }

    private static boolean isOpSuchtServer(Minecraft client, ModConfig config) {
        ServerData server = client.getCurrentServer();
        if (server == null || server.ip == null) return false;
        String ip = server.ip.toLowerCase(Locale.ROOT);
        for (String part : config.serverAddressContains) {
            if (part != null && !part.isBlank() && ip.contains(part.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
