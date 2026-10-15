package dev.opsuchtprices.mixin;

import dev.opsuchtprices.client.JobHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jede Actionbar läuft über Gui#setOverlayMessage – egal ob der Server sie als eigenes
 * Actionbar-Paket (OP SUCHT) oder als System-Chat mit overlay=true schickt.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void opsuchtprices$jobActionBar(Component message, boolean animate, CallbackInfo ci) {
        if (JobHud.onActionBar(message)) ci.cancel();
    }
}
