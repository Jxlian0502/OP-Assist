package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.job.JobTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag & Drop: Jobtracker-Fenster mit der Maus an die gewünschte Stelle ziehen. */
public final class JobHudMoveScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    private final Runnable save;
    private final JobTracker data;
    private int x, y, w = 120, h = 100;
    private boolean dragging;
    private double grabX, grabY;

    public JobHudMoveScreen(Screen parent, ModConfig config, Runnable save) {
        super(Component.literal("Jobtracker verschieben"));
        this.parent = parent;
        this.config = config;
        this.save = save;
        JobTracker live = JobHud.tracker();
        this.data = live != null && live.current() != null ? live : JobHud.previewTracker();
        this.x = config.jobHudX;
        this.y = config.jobHudY;
    }

    @Override
    protected void init() {
        int bw = 100;
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(width / 2 - bw - 4, height - 28, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
            x = 4;
            y = 4;
        }).bounds(width / 2 + 4, height - 28, bw, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, "Jobtracker mit der Maus an die gewünschte Stelle ziehen", width / 2, height - 52, 0xFFFFFFFF);
        x = Math.max(0, Math.min(x, width - w));
        y = Math.max(0, Math.min(y, height - h));
        int[] size = JobHud.renderPanel(g, x, y, data);
        if (size != null) {
            w = size[0];
            h = size[1];
        }
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, dragging ? 0xFFFFC94A : hover ? 0xFFFFFFFF : 0x80FFFFFF);
        g.drawCenteredString(font, "Position: " + x + ", " + y, width / 2, height - 40, 0xFFA8A8A8);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= x && event.x() < x + w && event.y() >= y && event.y() < y + h) {
            dragging = true;
            grabX = event.x() - x;
            grabY = event.y() - y;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            x = (int) Math.round(event.x() - grabX);
            y = (int) Math.round(event.y() - grabY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Für Tests. */
    public void moveTo(int nx, int ny) {
        x = nx;
        y = ny;
    }

    @Override
    public void onClose() {
        config.jobHudX = Math.max(0, x);
        config.jobHudY = Math.max(0, y);
        save.run();
        minecraft.setScreen(parent);
    }
}
