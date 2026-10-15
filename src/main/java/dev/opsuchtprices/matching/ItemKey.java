package dev.opsuchtprices.matching;

/**
 * Vergleichsschlüssel eines Items.
 *
 * @param material     Materialname, z. B. "NETHERITE_SWORD"
 * @param name         normalisierter eigener Name ("" = kein eigener Name)
 * @param enchantments Verzauberungs-Signatur ("" = keine)
 */
public record ItemKey(String material, String name, String enchantments) {

    public String asString() {
        return material + "|" + name + "|" + enchantments;
    }
}
