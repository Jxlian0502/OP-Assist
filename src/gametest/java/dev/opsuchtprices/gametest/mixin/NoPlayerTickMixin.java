package dev.opsuchtprices.gametest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NUR Test: Container-Bildschirme ohne Spielwelt nicht ticken lassen (sonst NullPointerException). */
@Mixin(AbstractContainerScreen.class)
public abstract class NoPlayerTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void opsuchtprices$test$noPlayer(CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) ci.cancel();
    }
}
