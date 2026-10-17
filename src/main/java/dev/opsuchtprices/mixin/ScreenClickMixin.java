package dev.opsuchtprices.mixin;

import dev.opsuchtprices.client.QualityOfLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Befehls-Schutz: Klick auf einen Chat-Text, der einen Befehl ausführt, erst nach Bestätigung. */
@Mixin(Screen.class)
public abstract class ScreenClickMixin {
    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void opsuchtprices$confirmCommand(ClickEvent event, Minecraft mc, Screen screen, CallbackInfo ci) {
        if (event instanceof ClickEvent.RunCommand run && QualityOfLife.confirmChatCommand(mc, screen, run.command())) {
            ci.cancel();
        }
    }
}
