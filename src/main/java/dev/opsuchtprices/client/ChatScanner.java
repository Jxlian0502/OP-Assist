package dev.opsuchtprices.client;

import dev.opsuchtprices.chat.ChatRepeatTracker;
import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.overlay.SlotInfo;
import dev.opsuchtprices.util.PriceFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.util.function.BooleanSupplier;

/** Markiert Chatnachrichten, die derselbe Spieler innerhalb des Zeitfensters schon einmal geschrieben hat. */
public final class ChatScanner {
    private ChatScanner() {}

    private static ModConfig config;
    private static BooleanSupplier active = () -> true;
    private static ChatRepeatTracker tracker;

    /** Kennung für markierte Zeilen: roter Strich links + roter Hintergrund (ChatComponentMixin). */
    public static final GuiMessageTag REPEAT_TAG = new GuiMessageTag(0xFFFF3030, null,
            Component.literal("Wiederholte Nachricht (Chatscanner)"), "Wiederholung");

    public static boolean isRepeat(GuiMessageTag tag) {
        return tag == REPEAT_TAG;
    }

    /** Textfarbe wiederholter Nachrichten. */
    private static final int RED_TEXT = 0xFF5555;

    /** Rot mit der Deckkraft des normalen Hintergrunds (etwas kräftiger). */
    public static int redBackground(int original) {
        int a = (original >>> 24) & 0xFF;
        a = Math.min(255, Math.round(a * 1.3f) + 20);
        return (a << 24) | 0xA01818;
    }

    public static void init(ModConfig modConfig, BooleanSupplier isActive) {
        config = modConfig;
        active = isActive;
        tracker = new ChatRepeatTracker(modConfig.chatScannerMinutes * 60_000L, modConfig.chatScannerMinLength);
    }

    private static boolean enabled() {
        return config != null && config.chatScanner && active.getAsBoolean();
    }

    /** Liefert den Treffer oder null (und merkt sich die Nachricht). */
    public static ChatRepeatTracker.Hit check(Component message) {
        if (!enabled()) return null;
        String text = SlotInfo.strip(message.getString());
        if (config.chatScannerSkipServer && (text.startsWith("OPSUCHT")
                || ChatRepeatTracker.split(text).sender().equalsIgnoreCase("OPSUCHT"))) return null;
        tracker.configure(config.chatScannerMinutes * 60_000L, config.chatScannerMinLength);
        return tracker.record(text, System.currentTimeMillis());
    }

    /** Für System-/Server-Chat (Fabric ALLOW_GAME): bei Wiederholung markiert selbst einfügen. */
    public static boolean allowGame(Component message, boolean overlay) {
        if (overlay) return true;
        return allowChat(message);
    }

    /** Für signierten Spieler-Chat (Fabric ALLOW_CHAT): bei Wiederholung markiert selbst einfügen. */
    public static boolean allowChat(Component message) {
        ChatRepeatTracker.Hit hit = check(message);
        if (hit == null) return true;
        Minecraft.getInstance().gui.getChat().addMessage(decorate(message, hit), null, REPEAT_TAG);
        return false;
    }

    public static MutableComponent decorate(Component message, ChatRepeatTracker.Hit hit) {
        Instant now = Instant.now();
        String who = hit.firstSender() == null || hit.firstSender().isBlank() ? "diesem Spieler" : hit.firstSender();
        Component hover = Component.literal("Gleiche Nachricht von " + who + "\n")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal((hit.count() - 1) + "× schon in den letzten " + config.chatScannerMinutes + " Min.\n")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Zuerst: " + PriceFormat.ago(Instant.ofEpochMilli(hit.firstAt()), now)
                        + "\nZuletzt: " + PriceFormat.ago(Instant.ofEpochMilli(hit.previousAt()), now))
                        .withStyle(ChatFormatting.GRAY));
        MutableComponent tag = Component.literal("⟳" + hit.count() + "× ")
                .withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true).withHoverEvent(new HoverEvent.ShowText(hover)));
        MutableComponent body;
        if (config == null || config.chatScannerRedText) {
            // Text rot einfärben (funktioniert auch, wenn ein anderer Mod wie LabyMod den Chat selbst zeichnet).
            // Klick-/Hover-Aktionen der einzelnen Teile bleiben erhalten.
            MutableComponent red = Component.empty();
            message.visit((style, text) -> {
                // alte §-Farbcodes im Text würden die rote Farbe überschreiben -> entfernen
                String plain = text.replaceAll("§[0-9a-fA-FrR]", "");
                if (!plain.isEmpty()) red.append(Component.literal(plain).setStyle(style.withColor(RED_TEXT)));
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            body = red;
        } else {
            body = message.copy();
        }
        return Component.empty().append(tag).append(body);
    }
}
