package dev.opsuchtprices.client;

import dev.opsuchtprices.tooltip.TooltipLine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Übersetzt Tooltip-Zeilen in farbige Minecraft-Komponenten. */
public final class TooltipComponents {
    private TooltipComponents() {}

    public static Component toComponent(TooltipLine line) {
        if (line.kind() == TooltipLine.Kind.MUTED) {
            return Component.literal(line.label()).withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.literal(line.label() + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(line.value()).withStyle(ChatFormatting.GOLD));
    }
}
