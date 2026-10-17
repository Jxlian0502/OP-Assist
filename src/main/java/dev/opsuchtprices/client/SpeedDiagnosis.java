package dev.opsuchtprices.client;

import dev.opsuchtprices.util.SpeedMath;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /opa speed: zeigt, woraus sich die Geschwindigkeit zusammensetzt (Item-Attribute, Effekte, alle
 * Modifikatoren, Lore, versteckte Item-Daten). Dient dazu, die Speed-Anzeige an OP SUCHT anzupassen.
 */
public final class SpeedDiagnosis {
    private SpeedDiagnosis() {}

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static List<String> lines(Player p) {
        List<String> out = new ArrayList<>();
        double base = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        double total = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        int lvl = InventoryButtons.speedEffectLevel(p);
        out.add("Speed-Anzeige: " + SpeedMath.format(InventoryButtons.speedPercent(p)) + " (Items "
                + SpeedMath.format(InventoryButtons.itemSpeedPercent(p)) + ", Schnelligkeit Stufe " + lvl
                + " als +" + (lvl * 20) + "% auf die Grundgeschwindigkeit)");
        out.add(String.format(Locale.ROOT, "Minecraft gesamt: %s (Basis %.4f, aktuell %.4f, sprintet: %s)",
                SpeedMath.format(base > 0 ? (total / base - 1) * 100 : 0), base, total, p.isSprinting() ? "ja" : "nein"));

        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            out.add("Alle Speed-Modifikatoren (" + inst.getModifiers().size() + "):");
            for (AttributeModifier m : inst.getModifiers()) {
                out.add(String.format(Locale.ROOT, "  %s: %s %.4f", m.id(), m.operation(), m.amount()));
            }
        }
        out.add("Effekte (" + p.getActiveEffects().size() + "):");
        for (MobEffectInstance e : p.getActiveEffects()) {
            out.add("  " + e.getEffect().getRegisteredName() + " Stufe " + (e.getAmplifier() + 1)
                    + " (" + e.getDuration() / 20 + " s)");
        }
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            out.add(slot.getName() + ": " + s.getHoverName().getString() + " [" + s.getItemHolder().getRegisteredName() + "]");
            ItemAttributeModifiers attrs = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            for (ItemAttributeModifiers.Entry e : attrs.modifiers()) {
                out.add(String.format(Locale.ROOT, "  Attribut %s %s %.4f (Slot %s)", e.attribute().getRegisteredName(),
                        e.modifier().operation(), e.modifier().amount(), e.slot().getSerializedName()));
            }
            ItemEnchantments ench = s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var en : ench.entrySet()) {
                out.add("  Verzauberung " + en.getKey().getRegisteredName() + " " + en.getIntValue());
            }
            ItemLore lore = s.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    String t = line.getString();
                    if (t.toLowerCase(Locale.ROOT).matches(".*(speed|schnell|geschwind|tempo|%).*")) out.add("  Lore: " + t);
                }
            }
            CustomData data = s.get(DataComponents.CUSTOM_DATA);
            if (data != null) {
                String t = data.copyTag().toString();
                out.add("  Daten: " + (t.length() > 400 ? t.substring(0, 400) + "…" : t));
            }
        }
        return out;
    }
}
