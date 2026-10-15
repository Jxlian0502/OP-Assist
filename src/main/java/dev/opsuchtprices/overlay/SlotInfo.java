package dev.opsuchtprices.overlay;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Inhalt eines Menü-Slots im Klartext (ohne §-Farbcodes), unabhängig von Minecraft.
 *
 * @param slot   Slot-Nummer im Menü
 * @param itemId z. B. "minecraft:sand"
 * @param count  Stapelgröße
 * @param name   angezeigter Name
 * @param lore   Beschreibungszeilen
 */
public record SlotInfo(int slot, String itemId, int count, String name, List<String> lore) {

    private static final Pattern CODES = Pattern.compile("§[0-9a-fk-orA-FK-ORxX]");

    public static String strip(String s) {
        return s == null ? "" : CODES.matcher(s).replaceAll("").trim();
    }

    public boolean isFiller() {
        return itemId.endsWith("_stained_glass_pane") && (name.isBlank() || (lore.isEmpty() && name.endsWith("Stained Glass Pane")));
    }

    public String loreStartingWith(String prefix) {
        for (String l : lore) {
            if (l.startsWith(prefix)) return l;
        }
        return null;
    }

    public boolean loreContains(String text) {
        for (String l : lore) {
            if (l.contains(text)) return true;
        }
        return false;
    }
}
