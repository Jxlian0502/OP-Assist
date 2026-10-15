package dev.opsuchtprices.gametest;

import dev.opsuchtprices.client.JobHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** NUR Test: zeigt das Jobtracker-HUD ohne Welt. */
public final class JobHudTestScreen extends Screen {
    public JobHudTestScreen() {
        super(Component.literal("Jobtracker-Test"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        JobHud.renderPanel(g, 4, 4);
    }
}
