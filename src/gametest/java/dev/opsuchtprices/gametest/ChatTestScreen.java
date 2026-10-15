package dev.opsuchtprices.gametest;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** NUR Test: zeigt Chatzeilen wie im Chatfenster. */
public final class ChatTestScreen extends Screen {
    private final List<Component> lines;

    public ChatTestScreen(List<Component> lines) {
        super(Component.literal("Chat-Test"));
        this.lines = lines;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        // echtes Chatfenster zeichnen (wie im Spiel, Chat offen)
        minecraft.gui.getChat().render(g, font, 0, mx, my, true, false);
    }
}
