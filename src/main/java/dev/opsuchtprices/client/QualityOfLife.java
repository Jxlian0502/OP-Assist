package dev.opsuchtprices.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.market.MarketEntry;
import dev.opsuchtprices.market.MarketService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Komfort-Funktionen: Warnung bei vollem Inventar, AFK-Warnung, Schilder-Suche, verkaufbare Items
 * hervorheben, Inhalts-Vorschau von Behälter-Items und Schutz vor Fehlklicks. Alles eigene Umsetzung.
 */
public final class QualityOfLife {
    private QualityOfLife() {}

    private static ModConfig config;
    private static BooleanSupplier onOpSucht = () -> true;
    private static MarketService market;

    public static void init(ModConfig cfg, BooleanSupplier active, MarketService marketService) {
        config = cfg;
        onOpSucht = active;
        market = marketService;
    }

    private static boolean on() {
        return config != null && onOpSucht.getAsBoolean();
    }

    /** Amboss-Eingabe: Kurzschreibweise ("1,5k") in volle Zahl umwandeln, sonst unverändert. */
    public static String anvilName(String name) {
        if (!on() || !config.numberShorthand) return name;
        String e = dev.opsuchtprices.util.NumberShorthand.expand(name);
        return e == null ? name : e;
    }

    /** /pay und /bank: Kurzschreibweise in volle Zahl umwandeln. */
    public static String command(String command) {
        if (!on() || !config.numberShorthand) return command;
        return dev.opsuchtprices.util.NumberShorthand.expandCommand(command);
    }

    // ---------- Warnungen (jeden Tick) ----------

    private static long lastInvWarn;
    private static Vec3 lastPos;
    private static float lastYaw, lastPitch;
    private static long lastMove = System.currentTimeMillis();
    private static long lastAfkWarn;

    public static void tick(Minecraft mc) {
        if (!on() || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (config.invFullWarning && isInventoryFull(mc) && now - lastInvWarn >= Math.max(1, config.invFullWarningSeconds) * 1000L) {
            lastInvWarn = now;
            mc.gui.setOverlayMessage(Component.literal("⚠ Inventar voll!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
            if (config.invFullWarningSound) {
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f));
            }
        }
        Vec3 pos = mc.player.position();
        if (lastPos == null || pos.distanceToSqr(lastPos) > 0.0001 || mc.player.getYRot() != lastYaw || mc.player.getXRot() != lastPitch) {
            lastPos = pos;
            lastYaw = mc.player.getYRot();
            lastPitch = mc.player.getXRot();
            lastMove = now;
            lastAfkWarn = 0;
        }
        long afkMs = Math.max(1, config.afkWarningMinutes) * 60_000L;
        if (config.afkWarning && now - lastAfkWarn >= 60_000L && mc.screen == null
                && dev.opsuchtprices.util.AfkRule.shouldWarn(now, lastMove, jobEventAgo(), afkMs)) {
            lastAfkWarn = now;
            mc.gui.setTimes(10, 60, 20);
            mc.gui.setSubtitle(Component.literal("Seit " + (now - lastMove) / 60_000 + " Min. nicht bewegt").withStyle(ChatFormatting.GRAY));
            mc.gui.setTitle(Component.literal("AFK?").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            if (config.afkWarningSound) {
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f));
            }
        }
    }

    private static Long jobEventAgoForTest;

    /** Wie lange die letzte Job-Meldung her ist (ms), null = noch kein Job. */
    private static Long jobEventAgo() {
        if (jobEventAgoForTest != null) return jobEventAgoForTest;
        var t = JobHud.tracker();
        var s = t == null ? null : t.current();
        return s == null ? null : t.sinceLastEventMs(s);
    }

    /** Für Tests: Zeit seit der letzten Job-Meldung vorgeben (null = echte Daten). */
    public static void setJobEventAgoForTest(Long ms) {
        jobEventAgoForTest = ms;
    }

    /** true, wenn keiner der 36 Inventar-Slots (Hotbar + Inventar) frei ist. */
    public static boolean isInventoryFull(Minecraft mc) {
        return mc.player != null && mc.player.getInventory().getFreeSlot() < 0;
    }

    /** Für Tests: Bewegungs-Zeitpunkt zurückdatieren. */
    public static void simulateIdle(long millis) {
        lastMove = System.currentTimeMillis() - millis;
        lastAfkWarn = 0;
    }

    // ---------- Verkaufbare Items hervorheben ----------

    /** Am Markt verkaufbar: normales Item (kein Custom Item) mit Kaufaufträgen am Markt. */
    public static boolean isSellable(ItemStack stack) {
        if (market == null || stack.isEmpty() || stack.has(DataComponents.CUSTOM_DATA) || stack.has(DataComponents.CUSTOM_NAME)) return false;
        MarketService.Snapshot snap = market.snapshot();
        if (snap == null) return false;
        String material = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(Locale.ROOT);
        for (MarketEntry e : snap.entries()) {
            if (e.material().equals(material)) return e.buyPrice() != null && e.buyOrders() > 0;
        }
        return false;
    }

    /** Verkaufsmenü: /sell oder Markt "Items verkaufen" (Titel enthält einen der eingestellten Begriffe). */
    public static boolean isSellMenu(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>) || screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return false;
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        for (String t : config.sellMenuTitles) if (t != null && !t.isBlank() && title.contains(t.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /**
     * Grüner Hintergrund hinter verkaufbaren Items, nur in Verkaufsmenüs. Wird vor dem Item gezeichnet
     * (Koordinaten relativ zum Menü), damit das Item selbst klar sichtbar bleibt.
     */
    public static void renderSlotBackground(Screen screen, GuiGraphics g, Slot s) {
        if (!on() || !config.highlightSellable || !s.hasItem() || !isSellMenu(screen)) return;
        market.refreshIfStale();
        if (!isSellable(s.getItem())) return;
        int x = s.x, y = s.y;
        g.fill(x, y, x + 16, y + 16, 0x3A3CE05A);          // zarter Hintergrund
        g.fill(x, y, x + 16, y + 1, 0xB05CE67A);           // feiner Rahmen
        g.fill(x, y + 15, x + 16, y + 16, 0xB05CE67A);
        g.fill(x, y + 1, x + 1, y + 15, 0xB05CE67A);
        g.fill(x + 15, y + 1, x + 16, y + 15, 0xB05CE67A);
    }

    // ---------- Inhalts-Vorschau (Talismanrucksack u. a.) ----------

    /** Items, die in den Daten eines Behälter-Items gespeichert sind (z. B. Talismanrucksack). */
    public static List<ItemStack> containedItems(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        Minecraft mc = Minecraft.getInstance();
        if (data == null || mc.level == null) return out;
        collect(data.copyTag(), mc, out, 0);
        return out;
    }

    private static void collect(Tag tag, Minecraft mc, List<ItemStack> out, int depth) {
        if (depth > 6 || out.size() >= 64) return;
        if (tag instanceof CompoundTag c) {
            if (c.contains("id") && (c.contains("count") || c.contains("components") || c.contains("Slot"))) {
                ItemStack.CODEC.parse(mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), c)
                        .result().filter(s -> !s.isEmpty()).ifPresent(out::add);
                return;
            }
            for (String key : c.keySet()) collect(c.get(key), mc, out, depth + 1);
        } else if (tag instanceof ListTag l) {
            for (Tag t : l) collect(t, mc, out, depth + 1);
        }
    }

    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        if (!on() || !config.bundlePreview) return;
        List<ItemStack> items = containedItems(stack);
        if (items.isEmpty()) return;
        lines.add(Component.literal("Inhalt:").withStyle(ChatFormatting.GOLD));
        int shown = 0;
        for (ItemStack it : items) {
            if (shown++ == 8) {
                lines.add(Component.literal("  … und " + (items.size() - 8) + " weitere").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            lines.add(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(it.getHoverName().copy())
                    .append(Component.literal(it.getCount() > 1 ? " x" + it.getCount() : "").withStyle(ChatFormatting.GRAY)));
        }
    }

    // ---------- Schilder-Suche ----------

    private static final List<BlockPos> signMatches = new ArrayList<>();
    private static long signUntil;

    /** Sucht in allen geladenen Chunks nach Schildern mit dem Text. Liefert die Treffer, nächste zuerst. */
    public static List<BlockPos> searchSigns(Minecraft mc, String query) {
        signMatches.clear();
        if (mc.level == null || mc.player == null || query == null || query.isBlank()) return List.of();
        String q = query.toLowerCase(Locale.ROOT);
        int r = mc.options.getEffectiveRenderDistance();
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                if (!(mc.level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SignBlockEntity sign && (matches(sign.getFrontText(), q) || matches(sign.getBackText(), q))) {
                        signMatches.add(be.getBlockPos());
                    }
                }
            }
        }
        BlockPos p = mc.player.blockPosition();
        signMatches.sort(Comparator.comparingDouble(b -> b.distSqr(p)));
        signUntil = System.currentTimeMillis() + 120_000L;
        return List.copyOf(signMatches);
    }

    private static boolean matches(SignText text, String q) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(text.getMessage(i, false).getString()).append(' ');
        return sb.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    public static void clearSignSearch() {
        signMatches.clear();
    }

    /** Leuchtende Umrandung um gefundene Schilder (2 Minuten lang). */
    public static void renderSignOutlines(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers) {
        if (signMatches.isEmpty() || buffers == null || poses == null) return;
        if (System.currentTimeMillis() > signUntil) {
            signMatches.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        VertexConsumer vc = buffers.getBuffer(RenderTypes.lines());
        for (BlockPos b : signMatches) {
            ShapeRenderer.renderShape(poses, vc, Shapes.block(), b.getX() - cam.x, b.getY() - cam.y, b.getZ() - cam.z, 0xFFFFD700, 3f);
        }
    }

    // ---------- Schutz vor Fehlklicks ----------

    // Sign-/Rename-Schutz: /sign und /rename erst nach Bestätigung im Chat (mit Farbvorschau) senden
    private static String pendingCommand;
    private static int pendingToken;
    private static long pendingAt;
    private static String bypass;

    /**
     * Für ClientSendMessageEvents.ALLOW_COMMAND: false = Befehl zurückhalten und stattdessen die
     * Bestätigung mit Vorschau im Chat zeigen.
     */
    public static boolean allowCommand(String command) {
        if (command == null) return true;
        if (command.equals(bypass)) {
            bypass = null;
            return true;
        }
        if (!on()) return true;
        String lower = command.toLowerCase(Locale.ROOT);
        boolean sign = config.signProtection && (lower.equals("sign") || lower.startsWith("sign "));
        boolean rename = config.renameProtection && lower.startsWith("rename ");
        if (!sign && !rename) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return true;
        String arg = command.indexOf(' ') < 0 ? "" : command.substring(command.indexOf(' ') + 1);
        pendingCommand = command;
        pendingToken = 1000 + new java.util.Random().nextInt(9000);
        pendingAt = System.currentTimeMillis();
        mc.player.displayClientMessage(protectionMessage(sign ? "SIGN-SCHUTZ" : "RENAME-SCHUTZ", sign, arg,
                mc.player.getMainHandItem(), pendingToken), false);
        return false;
    }

    /** Chat-Nachricht mit Item, Vorschau und Knöpfen Bestätigen / Abbrechen. */
    static Component protectionMessage(String title, boolean sign, String arg, ItemStack item, int token) {
        var msg = Component.empty()
                .append(Component.literal("⚠ " + title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\nItem: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[" + (item.isEmpty() ? "nichts in der Hand" : item.getHoverName().getString()) + "]")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(sign ? "\nVorschau: " : "\nNeuer Name: ").withStyle(ChatFormatting.GRAY));
        if (arg.isBlank()) {
            msg.append(Component.literal(sign ? "Ohne Text (nur Signatur)" : "(leer)").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            msg.append(dev.opsuchtprices.util.ColorCodes.toComponent(arg));
        }
        msg.append(Component.literal("\n"))
                .append(Component.literal("[ BESTÄTIGEN ]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/opa bestaetigen " + token))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("Befehl jetzt senden")))))
                .append(Component.literal("  "))
                .append(Component.literal("[ ABBRECHEN ]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/opa abbrechen " + token))));
        return msg;
    }

    /** Bestätigen-Knopf: zurückgehaltenen Befehl senden (gültig 60 s). Liefert eine Rückmeldung. */
    public static String confirmPending(int token) {
        Minecraft mc = Minecraft.getInstance();
        if (pendingCommand == null || token != pendingToken || System.currentTimeMillis() - pendingAt > 60_000L) {
            return "Nichts zu bestätigen (abgelaufen oder schon erledigt).";
        }
        String cmd = pendingCommand;
        pendingCommand = null;
        bypass = cmd;
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
        return "Gesendet: /" + cmd;
    }

    public static String cancelPending(int token) {
        if (pendingCommand == null || token != pendingToken) return "Nichts abzubrechen.";
        pendingCommand = null;
        return "Abgebrochen.";
    }

    /** Für Tests: zurückgehaltener Befehl. */
    public static String pendingCommand() {
        return pendingCommand;
    }

    public static int pendingToken() {
        return pendingToken;
    }

}
