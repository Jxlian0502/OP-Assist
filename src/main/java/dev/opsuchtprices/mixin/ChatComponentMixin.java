package dev.opsuchtprices.mixin;

import dev.opsuchtprices.client.ChatScanner;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Färbt den Zeilenhintergrund von Chatnachrichten rot, die der Chatscanner als Wiederholung markiert hat.
 * Ziel ist die Lambda in ChatComponent#render, die pro Zeile den schwarzen Hintergrund zeichnet
 * (1.21.11: method_75802, im Dev- und Produktionsnamen gleich).
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Redirect(method = "method_75802", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V"))
    private static void opsuchtprices$redBackground(ChatComponent.ChatGraphicsAccess access, int x0, int y0, int x1, int y1, int color,
                                                    int a0, int a1, ChatComponent.ChatGraphicsAccess a2, int a3, float a4,
                                                    GuiMessage.Line line, int a6, float a7) {
        access.fill(x0, y0, x1, y1, ChatScanner.isRepeat(line.tag()) ? ChatScanner.redBackground(color) : color);
    }
}
