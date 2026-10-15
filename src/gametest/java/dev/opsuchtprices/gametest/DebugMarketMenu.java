package dev.opsuchtprices.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NUR für Entwicklungstests: baut ein Menü aus einer Aufnahme-Datei nach
 * (aktiviert über -Dopsuchtprices.debugMenuFile). Im normalen Spiel ungenutzt.
 */
public final class DebugMarketMenu {
    private DebugMarketMenu() {}

    private static final Pattern HEAD = Pattern.compile("=== MENÜ \"(.*)\" \\| Typ minecraft:generic_9x(\\d)");
    private static final Pattern SLOT = Pattern.compile("^  \\[(\\d+)] (\\S+?)(?: x(\\d+))? \\| ?([^|]*?)(?: \\|.*)?$");

    public static ContainerScreen open(Path file, int index, Inventory inv) throws Exception {
        List<String> lines = Files.readAllLines(file);
        int n = -1;
        String title = null;
        int rows = 6;
        List<Object[]> slots = new ArrayList<>();
        Object[] cur = null;
        for (String line : lines) {
            Matcher h = HEAD.matcher(line);
            if (line.contains("=== MENÜ")) {
                n++;
                if (n == index && h.find()) {
                    title = h.group(1);
                    rows = Integer.parseInt(h.group(2));
                }
                continue;
            }
            if (n != index) continue;
            Matcher s = SLOT.matcher(line);
            if (s.find()) {
                cur = new Object[]{Integer.parseInt(s.group(1)), s.group(2),
                        s.group(3) == null ? 1 : Integer.parseInt(s.group(3)), s.group(4), new ArrayList<Component>()};
                slots.add(cur);
            } else if (line.startsWith("        ") && cur != null) {
                @SuppressWarnings("unchecked")
                List<Component> lore = (List<Component>) cur[4];
                lore.add(Component.literal(line.trim()));
            }
        }
        if (title == null) throw new IllegalArgumentException("Menü " + index + " nicht gefunden");

        ChestMenu menu = switch (rows) {
            case 3 -> ChestMenu.threeRows(999, inv);
            case 4 -> ChestMenu.fourRows(999, inv);
            default -> ChestMenu.sixRows(999, inv);
        };
        for (Object[] o : slots) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse((String) o[1]));
            ItemStack st = new ItemStack(item, (Integer) o[2]);
            String name = (String) o[3];
            st.set(DataComponents.CUSTOM_NAME, Component.literal(name)); // Server: Füllscheiben mit leerem Namen
            @SuppressWarnings("unchecked")
            List<Component> lore = (List<Component>) o[4];
            if (!lore.isEmpty()) st.set(DataComponents.LORE, new ItemLore(lore));
            menu.getContainer().setItem((Integer) o[0], st);
        }
        return new ContainerScreen(menu, inv, Component.literal(title));
    }
}
