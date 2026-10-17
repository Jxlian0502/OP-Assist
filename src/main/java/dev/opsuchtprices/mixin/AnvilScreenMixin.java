package dev.opsuchtprices.mixin;

import dev.opsuchtprices.client.QualityOfLife;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Kurzschreibweise im Amboss (Eingabefelder des Servers, z. B. Menge oder Preis): "1,5k" wird als "1500" gesendet. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
    @ModifyArg(method = "onNameChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ServerboundRenameItemPacket;<init>(Ljava/lang/String;)V"))
    private String opsuchtprices$expandShorthand(String name) {
        return QualityOfLife.anvilName(name);
    }
}
