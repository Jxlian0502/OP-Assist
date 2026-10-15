package dev.opsuchtprices.mixin;

import dev.opsuchtprices.client.GuiRecorder;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Meldet Menü-Klicks an die Aufnahme. Ändert nichts am Klick selbst. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void opsuchtprices$recordClick(int containerId, int slotId, int button, ClickType clickType,
                                           Player player, CallbackInfo ci) {
        GuiRecorder.onSlotClick(containerId, slotId, button, clickType);
    }

    @Inject(method = "handleInventoryButtonClick", at = @At("HEAD"))
    private void opsuchtprices$recordButton(int containerId, int buttonId, CallbackInfo ci) {
        GuiRecorder.onButtonClick(containerId, buttonId);
    }
}
