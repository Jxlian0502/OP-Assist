package dev.opsuchtprices.client;

import dev.opsuchtprices.cache.SaleTracker;
import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.matching.ItemMatcher;
import dev.opsuchtprices.tooltip.TooltipFormatter;
import dev.opsuchtprices.tooltip.TooltipLine;
import dev.opsuchtprices.util.ModLog;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Hängt "Letzter AH-Verkauf" und "Durchschnittspreis" an jeden Item-Tooltip an. */
public final class PriceTooltipHandler implements ItemTooltipCallback {

    private final SaleTracker tracker;
    private final ModConfig config;
    private final BooleanSupplier active;
    private long lastErrorLog = 0;

    public PriceTooltipHandler(SaleTracker tracker, ModConfig config, BooleanSupplier active) {
        this.tracker = tracker;
        this.config = config;
        this.active = active;
    }

    @Override
    public void getTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType,
                           List<Component> lines) {
        if (stack == null || stack.isEmpty() || !config.enabled || !active.getAsBoolean()) return;
        try {
            String key = ItemMatcher.keyFor(StackSignatures.of(stack)).asString();
            List<TooltipLine> out = TooltipFormatter.build(tracker.sales().summary(key),
                    config.currencyLabel, tracker.clock().instant());
            lines.add(Component.empty());
            for (TooltipLine l : out) {
                lines.add(TooltipComponents.toComponent(l));
            }
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLog > 30_000) {
                lastErrorLog = now;
                ModLog.warn("Fehler beim Erzeugen des Tooltips: {}", e.toString());
            }
        }
    }
}
