package dev.opsuchtprices.client;

import dev.opsuchtprices.util.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Nimmt Server-Menüs auf (nur wenn per Befehl eingeschaltet): Titel, Inhalt aller
 * Menü-Slots (ohne das eigene Inventar), jeden Klick und Server-Nachrichten.
 * Dient dazu, das Markt-Overlay exakt an das OP SUCHT Menü anzupassen.
 * Es wird nichts gesendet, nur lokal in eine Textdatei geschrieben.
 */
public final class GuiRecorder {
    private GuiRecorder() {}

    private static volatile Path file;
    private static Screen lastScreen;
    private static String lastWritten = "";
    private static String pending = "";
    private static int stableTicks;
    private static int menus;
    private static int clicks;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static boolean isRecording() {
        return file != null;
    }

    public static void start(Path target, String serverAddress) {
        file = null;
        lastScreen = null;
        lastWritten = "";
        pending = "";
        stableTicks = 0;
        menus = 0;
        clicks = 0;
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, "OP-Assistent – Menü-Aufnahme\n"
                    + "Start: " + java.time.LocalDateTime.now().withNano(0) + "\n"
                    + "Server: " + serverAddress + "\n\n", StandardCharsets.UTF_8);
            file = target;
        } catch (IOException e) {
            ModLog.warn("Aufnahme konnte nicht gestartet werden: {}", e.toString());
        }
    }

    /** Beendet die Aufnahme. Liefert die Datei oder null. */
    public static Path stop() {
        Path f = file;
        if (f != null) write("\n=== Ende der Aufnahme: " + menus + " Menüs, " + clicks + " Klicks ===\n");
        file = null;
        return f;
    }

    public static int menus() {
        return menus;
    }

    public static int clicks() {
        return clicks;
    }

    /** Jeden Client-Tick aufrufen. */
    public static void tick(Minecraft mc) {
        if (file == null) return;
        Screen screen = mc.screen;
        if (screen != lastScreen) {
            lastScreen = screen;
            pending = "";
            stableTicks = 0;
            if (screen == null) {
                write(time() + " Fenster geschlossen\n");
            } else if (!(screen instanceof AbstractContainerScreen<?>)) {
                write(time() + " Fenster geöffnet: " + screen.getClass().getSimpleName()
                        + " | Titel: " + screen.getTitle().getString() + "\n");
            }
        }
        if (screen instanceof AbstractContainerScreen<?> cs) {
            String snap = snapshot(cs);
            if (!snap.equals(pending)) {
                pending = snap;
                stableTicks = 0;
            } else if (++stableTicks == 4 && !snap.equals(lastWritten)) {
                // Inhalt ist seit 4 Ticks unverändert: vollständig geladen, aufschreiben
                lastWritten = snap;
                menus++;
                write("\n" + time() + " " + snap + "\n");
            }
        }
    }

    /** Aufgerufen, bevor der Client einen Slot-Klick an den Server schickt. */
    public static void onSlotClick(int containerId, int slotId, int button, ClickType type) {
        if (file == null) return;
        clicks++;
        String item = "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu != null && slotId >= 0 && slotId < menu.slots.size()) {
                Slot s = menu.slots.get(slotId);
                item = s.container instanceof Inventory ? " (eigenes Inventar)" : "";
                ItemStack st = s.getItem();
                if (!st.isEmpty()) item += " auf: " + st.getHoverName().getString();
            }
        }
        String btn = button == 0 ? "Linksklick" : button == 1 ? "Rechtsklick" : "Taste " + button;
        write(time() + " >>> KLICK Slot " + slotId + " | " + btn + " | " + type + item + "\n");
    }

    public static void onButtonClick(int containerId, int buttonId) {
        if (file == null) return;
        clicks++;
        write(time() + " >>> MENÜ-KNOPF " + buttonId + "\n");
    }

    public static void onServerMessage(Component message, boolean overlay) {
        if (file == null) return;
        write(time() + (overlay ? " Actionbar: " : " Chat: ") + message.getString() + "\n");
    }

    private static String snapshot(AbstractContainerScreen<?> cs) {
        AbstractContainerMenu menu = cs.getMenu();
        StringBuilder sb = new StringBuilder();
        String type;
        try {
            Identifier id = BuiltInRegistries.MENU.getKey(menu.getType());
            type = id == null ? "?" : id.toString();
        } catch (RuntimeException e) {
            type = "inventar";
        }
        int size = 0;
        for (Slot s : menu.slots) if (!(s.container instanceof Inventory)) size++;
        sb.append("=== MENÜ \"").append(cs.getTitle().getString()).append("\" | Typ ").append(type)
                .append(" | ").append(size).append(" Slots ===\n");
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            sb.append("  [").append(s.index).append("] ")
                    .append(BuiltInRegistries.ITEM.getKey(st.getItem()));
            if (st.getCount() > 1) sb.append(" x").append(st.getCount());
            sb.append(" | ").append(st.getHoverName().getString());
            Identifier model = st.get(DataComponents.ITEM_MODEL);
            if (model != null && !model.equals(BuiltInRegistries.ITEM.getKey(st.getItem()))) {
                sb.append(" | Modell ").append(model);
            }
            CustomModelData cmd = st.get(DataComponents.CUSTOM_MODEL_DATA);
            if (cmd != null) sb.append(" | CMD ").append(cmd.floats()).append(cmd.strings());
            sb.append('\n');
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    String t = line.getString();
                    if (!t.isBlank()) sb.append("        ").append(t).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String time() {
        return LocalTime.now().format(TIME);
    }

    private static void write(String s) {
        Path f = file;
        if (f == null) return;
        try {
            Files.writeString(f, s, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ModLog.warn("Aufnahme: Schreiben fehlgeschlagen: {}", e.toString());
        }
    }
}
