package dev.opsuchtprices.client;

import dev.opsuchtprices.matching.ItemMatcher;
import dev.opsuchtprices.matching.ItemSignature;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Liest aus einem ItemStack Material, eigenen Namen und Verzauberungen,
 * um ihn mit den gespeicherten Verkäufen abzugleichen.
 */
public final class StackSignatures {
    private StackSignatures() {}

    public static ItemSignature of(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String material = ItemMatcher.materialFromItemId(itemId);

        // Eigener Name: custom_name, ersatzweise ein vom Standard abweichender item_name
        String name = null;
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            name = custom.getString();
        } else {
            Component itemName = stack.get(DataComponents.ITEM_NAME);
            Component defaultName = stack.getItem().components().get(DataComponents.ITEM_NAME);
            if (itemName != null && (defaultName == null
                    || !itemName.getString().equals(defaultName.getString()))) {
                name = itemName.getString();
            }
        }

        SortedMap<String, Integer> ench = new TreeMap<>();
        addEnchantments(ench, stack.get(DataComponents.ENCHANTMENTS));
        addEnchantments(ench, stack.get(DataComponents.STORED_ENCHANTMENTS)); // verzauberte Bücher
        return new ItemSignature(material, name, ench);
    }

    private static void addEnchantments(SortedMap<String, Integer> out, ItemEnchantments ench) {
        if (ench == null || ench.isEmpty()) return;
        for (var entry : ench.entrySet()) {
            out.put(entry.getKey().getRegisteredName(), entry.getIntValue());
        }
    }
}
