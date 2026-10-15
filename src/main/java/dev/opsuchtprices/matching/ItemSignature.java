package dev.opsuchtprices.matching;

import dev.opsuchtprices.model.AuctionItem;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Merkmale eines Items für den Vergleich (aus einem ItemStack oder einer Auktion).
 *
 * @param material     Materialname in Großbuchstaben
 * @param name         eigener Name im Klartext, null für Vanilla-Namen
 * @param enchantments Verzauberungen "minecraft:x" -> Level
 */
public record ItemSignature(String material, String name, SortedMap<String, Integer> enchantments) {

    public static ItemSignature of(AuctionItem item) {
        return new ItemSignature(item.material(), item.displayName(), new TreeMap<>(item.enchantments()));
    }
}
