package dev.opsuchtprices.gametest.mixin;

import dev.opsuchtprices.gametest.MarketOverlayGameTest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NUR Test: fängt den Slot-Klick des Overlays ab, protokolliert ihn und sendet nichts. */
@Pseudo
@Mixin(targets = "dev.opsuchtprices.client.MarketOverlay", remap = false)
public abstract class TestHooksMixin {
    @Inject(method = "sendClick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void opsuchtprices$test$capture(AbstractContainerMenu menu, int slot, int button,
                                                   CallbackInfoReturnable<Boolean> cir) {
        MarketOverlayGameTest.CLICKS.add(slot + ":" + button);
        cir.setReturnValue(true);
    }
}
