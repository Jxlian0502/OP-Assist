package dev.opsuchtprices.matching;

import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Erzeugt Vergleichsschlüssel. Dieselbe Funktion für Spiel-Items und
 * API-Auktionen, damit beide Seiten gleich behandelt werden.
 *
 * Gleich sind zwei Items nur bei gleichem Material, gleichem eigenen Namen
 * (ohne Farbcodes, Groß-/Kleinschreibung egal) und exakt gleichen Verzauberungen.
 */
public final class ItemMatcher {
    private ItemMatcher() {}

    public static ItemKey keyFor(ItemSignature sig) {
        String material = sig.material() == null ? "" : sig.material().trim().toUpperCase(Locale.ROOT);
        return new ItemKey(material, NameNormalizer.normalize(sig.name()), enchantSignature(sig.enchantments()));
    }

    static String enchantSignature(Map<String, Integer> ench) {
        if (ench == null || ench.isEmpty()) return "";
        StringJoiner j = new StringJoiner(",");
        ench.entrySet().stream()
                .map(e -> Map.entry(normalizeEnchant(e.getKey()), e.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> j.add(e.getKey() + "=" + e.getValue()));
        return j.toString();
    }

    static String normalizeEnchant(String key) {
        String k = key.toLowerCase(Locale.ROOT).trim();
        return k.contains(":") ? k : "minecraft:" + k;
    }

    /** "minecraft:netherite_axe" bzw. "netherite_axe" -> "NETHERITE_AXE". */
    public static String materialFromItemId(String namespacedId) {
        String path = namespacedId.contains(":") ? namespacedId.substring(namespacedId.indexOf(':') + 1) : namespacedId;
        return path.toUpperCase(Locale.ROOT);
    }
}
