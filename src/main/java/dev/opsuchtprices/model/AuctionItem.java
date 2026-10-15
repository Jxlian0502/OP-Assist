package dev.opsuchtprices.model;

import java.util.SortedMap;

/**
 * Das "item"-Objekt einer Auktion.
 *
 * @param material     Bukkit-Materialname, z. B. "NETHERITE_AXE"
 * @param displayName  Klartext-Name, null wenn die API keinen liefert
 * @param amount       Stapelgröße (mindestens 1)
 * @param enchantments Verzauberungen, z. B. "minecraft:sharpness" -> 5
 * @param icon         Bild-URL aus der API (Custom Items: items.opsucht.net), kann null sein
 */
public record AuctionItem(
        String material,
        String displayName,
        int amount,
        SortedMap<String, Integer> enchantments,
        String icon) {
}
