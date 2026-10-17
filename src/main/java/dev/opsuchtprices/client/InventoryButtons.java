package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.util.SpeedMath;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Knopfleiste und Speed-Anzeige im eigenen Inventar (Taste E). Ein Klick auf einen Knopf führt seinen
 * Befehl aus (z. B. /bank); Leiste und Speed-Anzeige lassen sich per Drag & Drop verschieben. Die
 * Position hängt an der Inventar-Ecke und wird in der Config gespeichert.
 */
public final class InventoryButtons {
    private InventoryButtons() {}

    static final int SIZE = 20;
    static final int GAP = 2;
    /** Ab so vielen Pixeln Mausbewegung wird aus einem Klick ein Verschieben. */
    static final double DRAG_THRESHOLD = 3;

    private static ModConfig config;
    private static BooleanSupplier active = () -> true;
    private static Runnable save = () -> {};

    private enum Target { BUTTON, SPEED }

    private static Target pressed;
    private static int pressedButton = -1;
    private static double pressX, pressY;
    private static int startOffX, startOffY;
    private static boolean dragging;

    /** Inventar-Ecke beim letzten Zeichnen (für Tests). */
    public static volatile int lastLeft, lastTop;

    /** Mitte des Knopfs i in GUI-Koordinaten (für Tests). */
    public static int[] buttonCenter(Screen s, int i) {
        return new int[]{buttonX(s, lastLeft, i) + SIZE / 2, buttonY(s, lastTop, i) + SIZE / 2};
    }

    /** Zuletzt ausgeführter Befehl (für Tests). */
    public static volatile String lastCommand;

    public static void init(ModConfig cfg, BooleanSupplier isActive, Runnable saveConfig) {
        config = cfg;
        active = isActive;
        save = saveConfig;
    }

    static boolean applies(Screen screen) {
        return config != null && screen instanceof InventoryScreen && active.getAsBoolean()
                && (config.inventoryButtons || config.inventorySpeed);
    }

    // ---------- Position ----------

    private static int count() {
        return config.inventoryButtonList == null ? 0 : config.inventoryButtonList.size();
    }

    /** Position des Knopfs relativ zur Inventar-Ecke: eigene Position oder Platz in der Standardreihe. */
    static int offX(int i) {
        ModConfig.InventoryButton b = config.inventoryButtonList.get(i);
        return b.x != null ? b.x : config.inventoryButtonsX + i * (SIZE + GAP);
    }

    static int offY(int i) {
        ModConfig.InventoryButton b = config.inventoryButtonList.get(i);
        return b.y != null ? b.y : config.inventoryButtonsY;
    }

    static int buttonX(Screen s, int left, int i) {
        return clamp(left + offX(i), 0, s.width - SIZE);
    }

    static int buttonY(Screen s, int top, int i) {
        return clamp(top + offY(i), 0, s.height - SIZE);
    }

    private static int speedW(Font font) {
        return Math.max(font.width("Speed"), font.width("+0000%"));
    }

    static int speedX(Screen s, int left, Font font) {
        return clamp(left + config.inventorySpeedX, 0, s.width - speedW(font));
    }

    static int speedY(Screen s, int top) {
        return clamp(top + config.inventorySpeedY, 0, s.height - 20);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), v));
    }

    /** Index des Knopfs unter der Maus oder -1. */
    private static int buttonAt(Screen s, int left, int top, double mx, double my) {
        if (!config.inventoryButtons) return -1;
        // von hinten: bei überlappenden Knöpfen gewinnt der oben gezeichnete
        for (int i = count() - 1; i >= 0; i--) {
            int x = buttonX(s, left, i), y = buttonY(s, top, i);
            if (mx >= x && mx < x + SIZE && my >= y && my < y + SIZE) return i;
        }
        return -1;
    }

    private static boolean overSpeed(Screen s, int left, int top, double mx, double my, Font font) {
        if (!config.inventorySpeed) return false;
        int x = speedX(s, left, font), y = speedY(s, top);
        return mx >= x && mx < x + speedW(font) && my >= y && my < y + 20;
    }

    // ---------- Zeichnen ----------

    public static void render(Screen screen, int left, int top, GuiGraphics g, int mx, int my) {
        if (!applies(screen)) return;
        lastLeft = left;
        lastTop = top;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int hovered = buttonAt(screen, left, top, mx, my);
        if (config.inventoryButtons) {
            int draggedButton = dragging && pressed == Target.BUTTON ? pressedButton : -1;
            for (int i = 0; i < count(); i++) {
                if (i != draggedButton) drawButton(g, screen, left, top, i, i == hovered && !dragging);
            }
            if (draggedButton >= 0) drawButton(g, screen, left, top, draggedButton, true); // gezogener Knopf obenauf
            if (hovered >= 0 && !dragging) {
                ModConfig.InventoryButton b = config.inventoryButtonList.get(hovered);
                g.setTooltipForNextFrame(font, List.of(
                        Component.literal(b.label == null ? "/" + b.command : b.label).getVisualOrderText(),
                        Component.literal("Klick: /" + b.command).withStyle(ChatFormatting.GRAY).getVisualOrderText(),
                        Component.literal("Ziehen: Knopf verschieben").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()),
                        mx, my);
            }
        }
        if (config.inventorySpeed && mc.player != null) {
            int x = speedX(screen, left, font), y = speedY(screen, top);
            g.drawString(font, "Speed", x, y, 0xFFC77DFF);
            g.drawString(font, SpeedMath.format(speedPercent(mc.player)), x, y + 10, 0xFF7CDB6A);
            if (overSpeed(screen, left, top, mx, my, font) && hovered < 0 && !dragging) {
                g.setTooltipForNextFrame(font, List.of(
                        Component.literal("Speed aus Items (Hand, Nebenhand, Rüstung)").getVisualOrderText(),
                        Component.literal("plus Schnelligkeits-Effekt (x2 mit Speed Boots)").withStyle(ChatFormatting.GRAY).getVisualOrderText(),
                        Component.literal("Ziehen: Anzeige verschieben").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()),
                        mx, my);
            }
        }
    }

    private static void drawButton(GuiGraphics g, Screen screen, int left, int top, int i, boolean hot) {
        ModConfig.InventoryButton b = config.inventoryButtonList.get(i);
        int bx = buttonX(screen, left, i), y = buttonY(screen, top, i);
        g.fill(bx, y, bx + SIZE, y + SIZE, hot ? 0xFF5A5A6E : 0xFF2B2B35);
        g.fill(bx, y, bx + SIZE, y + 1, 0xFF6E6E80);
        g.fill(bx, y + SIZE - 1, bx + SIZE, y + SIZE, 0xFF15151B);
        g.renderItem(icon(b.item), bx + 2, y + 2);
    }

    private static ItemStack icon(String id) {
        Identifier ident = id == null ? null : Identifier.tryParse(id);
        Item item = ident == null ? Items.BARRIER : BuiltInRegistries.ITEM.getValue(ident);
        return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
    }

    // ---------- Speed ----------

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * Angezeigter Speed wie bei OP SUCHT (gleicher Wert wie die OPMOD): Item-Speed plus Schnelligkeits-Effekt,
     * der Effekt mal Faktor aus "x2 Speed Effekt"-Items (z. B. Speed Boots).
     */
    public static double speedPercent(Player player) {
        return SpeedMath.opSuchtPercent(itemSpeedPercent(player), speedEffectLevel(player), effectMultiplier(player));
    }

    /** Stufe des Schnelligkeits-Effekts (0 = keiner). */
    public static int speedEffectLevel(Player player) {
        var e = player.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
        return e == null ? 0 : e.getAmplifier() + 1;
    }

    /** Faktor für den Schnelligkeits-Effekt aus der Lore der getragenen/gehaltenen Items ("x2 Speed Effekt"). */
    public static int effectMultiplier(Player player) {
        List<String> lines = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            var lore = player.getItemBySlot(slot).get(DataComponents.LORE);
            if (lore != null) for (Component c : lore.lines()) lines.add(c.getString());
        }
        return SpeedMath.effectMultiplier(lines);
    }

    /** Speed-Bonus in Prozent nur aus den Bewegungsgeschwindigkeits-Attributen der getragenen/gehaltenen Items. */
    public static double itemSpeedPercent(Player player) {
        List<SpeedMath.Mod> mods = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ItemAttributeModifiers attrs = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            for (ItemAttributeModifiers.Entry e : attrs.modifiers()) {
                if (e.attribute().value() != Attributes.MOVEMENT_SPEED.value() || !e.slot().test(slot)) continue;
                AttributeModifier m = e.modifier();
                SpeedMath.Op op = switch (m.operation()) {
                    case ADD_VALUE -> SpeedMath.Op.ADD_VALUE;
                    case ADD_MULTIPLIED_BASE -> SpeedMath.Op.ADD_MULTIPLIED_BASE;
                    case ADD_MULTIPLIED_TOTAL -> SpeedMath.Op.ADD_MULTIPLIED_TOTAL;
                };
                mods.add(new SpeedMath.Mod(op, m.amount()));
            }
        }
        return SpeedMath.percent(player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), mods);
    }

    // ---------- Maus: Klick = Befehl, Ziehen = Verschieben ----------

    public static boolean mouseClicked(Screen screen, int left, int top, double mx, double my, int button) {
        if (!applies(screen) || button != 0) return false;
        Font font = Minecraft.getInstance().font;
        int i = buttonAt(screen, left, top, mx, my);
        Target t = i >= 0 ? Target.BUTTON : overSpeed(screen, left, top, mx, my, font) ? Target.SPEED : null;
        if (t == null) return false;
        pressed = t;
        pressedButton = i;
        pressX = mx;
        pressY = my;
        startOffX = t == Target.BUTTON ? offX(i) : config.inventorySpeedX;
        startOffY = t == Target.BUTTON ? offY(i) : config.inventorySpeedY;
        dragging = false;
        return true;
    }

    public static boolean mouseDragged(Screen screen, double mx, double my) {
        if (pressed == null || !applies(screen)) return false;
        if (!dragging && Math.hypot(mx - pressX, my - pressY) >= DRAG_THRESHOLD) dragging = true;
        if (dragging) {
            int nx = startOffX + (int) Math.round(mx - pressX);
            int ny = startOffY + (int) Math.round(my - pressY);
            if (pressed == Target.BUTTON) {
                ModConfig.InventoryButton b = config.inventoryButtonList.get(pressedButton);
                b.x = nx;
                b.y = ny;
            } else {
                config.inventorySpeedX = nx;
                config.inventorySpeedY = ny;
            }
        }
        return true;
    }

    public static boolean mouseReleased(Screen screen, int left, int top, double mx, double my, int button) {
        if (pressed == null || button != 0) return false;
        Target t = pressed;
        boolean wasDrag = dragging;
        int i = pressedButton;
        pressed = null;
        pressedButton = -1;
        dragging = false;
        if (wasDrag) {
            save.run();
        } else if (t == Target.BUTTON && i >= 0 && i == buttonAt(screen, left, top, mx, my)) {
            run(config.inventoryButtonList.get(i).command);
        }
        return true;
    }

    private static void run(String command) {
        if (command == null || command.isBlank()) return;
        String c = command.trim().startsWith("/") ? command.trim().substring(1) : command.trim();
        lastCommand = c;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(c);
    }
}
