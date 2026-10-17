package dev.opsuchtprices.mixin;

import dev.opsuchtprices.client.MarketOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import dev.opsuchtprices.client.InventoryButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ersetzt die Darstellung des OP SUCHT Marktplatz-Menüs durch das Overlay.
 * Für alle anderen Menüs ändert sich nichts.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    private AbstractContainerScreen<?> opsuchtprices$self() {
        return (AbstractContainerScreen<?>) (Object) this;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void opsuchtprices$render(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MarketOverlay.render(opsuchtprices$self(), g, mouseX, mouseY)) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void opsuchtprices$renderToggle(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MarketOverlay.renderOriginalToggle(opsuchtprices$self(), g, mouseX, mouseY);
    }

    // renderContents statt render: das Inventar (mit Rezeptbuch) ruft render der Oberklasse nicht auf
    @Inject(method = "renderContents", at = @At("TAIL"))
    private void opsuchtprices$renderInventoryButtons(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryButtons.render(opsuchtprices$self(), leftPos, topPos, g, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void opsuchtprices$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryButtons.mouseClicked(opsuchtprices$self(), leftPos, topPos, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (MarketOverlay.mouseClicked(opsuchtprices$self(), event.x(), event.y(), event.button())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void opsuchtprices$release(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryButtons.mouseReleased(opsuchtprices$self(), leftPos, topPos, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (MarketOverlay.isActive(opsuchtprices$self())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void opsuchtprices$drag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryButtons.mouseDragged(opsuchtprices$self(), event.x(), event.y())) {
            cir.setReturnValue(true);
            return;
        }
        if (MarketOverlay.isActive(opsuchtprices$self())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void opsuchtprices$scroll(double x, double y, double sx, double sy, CallbackInfoReturnable<Boolean> cir) {
        if (MarketOverlay.mouseScrolled(opsuchtprices$self(), x, y, sy)) cir.setReturnValue(true);
    }
}
