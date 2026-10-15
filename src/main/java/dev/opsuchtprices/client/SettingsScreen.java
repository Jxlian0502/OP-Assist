package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.job.PauseFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen des OP-Assistenten (aus dem Mod-Menü oder per /opa einstellungen). */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    private final Runnable save;
    private PauseSlider slider;
    private EditBox pauseBox;
    private boolean syncing;

    public SettingsScreen(Screen parent, ModConfig config, Runnable save) {
        super(Component.literal("OP-Assistent – Einstellungen"));
        this.parent = parent;
        this.config = config;
        this.save = save;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = 56;
        // Pausezeit: Schieberegler (1 s – 5 Min.) + Freitext daneben
        slider = new PauseSlider(cx - 155, y, 200, 20, config.jobAutoPauseSeconds);
        addRenderableWidget(slider);
        pauseBox = new EditBox(font, cx + 51, y, 104, 20, Component.literal("Pausezeit"));
        pauseBox.setMaxLength(12);
        pauseBox.setValue(PauseFormat.formatInput(config.jobAutoPauseSeconds));
        pauseBox.setHint(Component.literal("z. B. 1:30"));
        pauseBox.setResponder(text -> {
            if (syncing) return;
            int sec = PauseFormat.parse(text);
            pauseBox.setTextColor(sec < 0 ? 0xFFFF5555 : 0xFFE0E0E0);
            if (sec > 0) {
                config.jobAutoPauseSeconds = sec;
                syncing = true;
                slider.setSeconds(sec);
                syncing = false;
            }
        });
        addRenderableWidget(pauseBox);

        y += 34;
        addRenderableWidget(Button.builder(Component.literal("Jobtracker: " + (config.jobTracker ? "AN" : "AUS")), b -> {
            config.jobTracker = !config.jobTracker;
            b.setMessage(Component.literal("Jobtracker: " + (config.jobTracker ? "AN" : "AUS")));
        }).bounds(cx - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Jobtracker verschieben…"),
                b -> minecraft.setScreen(new JobHudMoveScreen(this, config, save))).bounds(cx + 5, y, 150, 20).build());

        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Job-Actionbar: " + (config.jobHideActionBar ? "AUS" : "AN")), b -> {
            config.jobHideActionBar = !config.jobHideActionBar;
            b.setMessage(Component.literal("Job-Actionbar: " + (config.jobHideActionBar ? "AUS" : "AN")));
        }).bounds(cx - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Chatscanner: " + (config.chatScanner ? "AN" : "AUS")), b -> {
            config.chatScanner = !config.chatScanner;
            b.setMessage(Component.literal("Chatscanner: " + (config.chatScanner ? "AN" : "AUS")));
        }).bounds(cx + 5, y, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 100, height - 28, 200, 20).build());
    }

    private void onSliderChanged(int seconds) {
        config.jobAutoPauseSeconds = seconds;
        if (syncing || pauseBox == null) return;
        syncing = true;
        pauseBox.setValue(PauseFormat.formatInput(seconds));
        pauseBox.setTextColor(0xFFE0E0E0);
        syncing = false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, "OP-Assistent – by Jxlian0502", width / 2, 14, 0xFFFFC94A);
        g.drawCenteredString(font, "Einstellungen", width / 2, 26, 0xFFA8A8A8);
        g.drawString(font, "Jobtracker pausiert nach (ohne Job-Meldung):", width / 2 - 155, 44, 0xFFFFFFFF);
        g.drawString(font, "Freitext: 45s · 1:30 · 2m (max. 60 Min.)", width / 2 - 155, 79, 0xFF808080);

        // Statistik (dauerhaft, wird nie zurückgesetzt)
        int sx = width / 2 - 155, sy = 142, sw = 310;
        g.fill(sx, sy, sx + sw, sy + 66, 0x80000000);
        g.drawString(font, "Statistik", sx + 6, sy + 5, 0xFFFFC94A);
        String w = StatsTracker.world();
        boolean ok = StatsTracker.countingAllowed();
        String where = "Welt: " + (w == null ? "unbekannt" : w) + (ok ? " – zählt" : " – zählt nicht");
        g.drawString(font, where, sx + sw - 6 - font.width(where), sy + 5, ok ? 0xFF7CDB6A : 0xFFFF7A5A);
        var st = StatsTracker.stats();
        long trees = st == null ? 0 : st.trees(), ores = st == null ? 0 : st.diamondOres(), sp = st == null ? 0 : st.spawners();
        statRow(g, sx, sy + 17, sw, "Bäume gefällt", trees, 0xFF8FD16A, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG));
        statRow(g, sx, sy + 29, sw, "Diamanterze abgebaut", ores, 0xFF6AE0E0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_ORE));
        statRow(g, sx, sy + 41, sw, "Spawner abgebaut", sp, 0xFFC77DFF, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPAWNER));
        g.drawString(font, "Zählt nur auf Farmwelt 1–12 und Nether 1–2", sx + 6, sy + 55, 0xFF808080);
    }

    private void statRow(GuiGraphics g, int x, int y, int w, String label, long value, int color, net.minecraft.world.item.ItemStack icon) {
        g.pose().pushMatrix();
        g.pose().translate(x + 6, y - 2);
        g.pose().scale(0.75f, 0.75f);
        g.renderItem(icon, 0, 0);
        g.pose().popMatrix();
        g.drawString(font, label, x + 22, y, 0xFFE0E0E0);
        String v = dev.opsuchtprices.util.PriceFormat.number(value);
        g.drawString(font, v, x + w - 6 - font.width(v), y, color);
    }

    /** Für Tests. */
    public void setPauseText(String text) {
        pauseBox.setValue(text);
    }

    @Override
    public void onClose() {
        save.run();
        minecraft.setScreen(parent);
    }

    /** Schieberegler 1 s – 5 Min. in 1-s-Schritten. */
    private final class PauseSlider extends AbstractSliderButton {
        PauseSlider(int x, int y, int w, int h, int seconds) {
            super(x, y, w, h, Component.empty(), toValue(seconds));
            updateMessage();
        }

        private static double toValue(int seconds) {
            int s = Math.max(PauseFormat.MIN_SECONDS, Math.min(seconds, PauseFormat.SLIDER_MAX_SECONDS));
            return (s - PauseFormat.MIN_SECONDS) / (double) (PauseFormat.SLIDER_MAX_SECONDS - PauseFormat.MIN_SECONDS);
        }

        int seconds() {
            return PauseFormat.MIN_SECONDS + (int) Math.round(value * (PauseFormat.SLIDER_MAX_SECONDS - PauseFormat.MIN_SECONDS));
        }

        void setSeconds(int seconds) {
            value = toValue(seconds);
            setMessage(Component.literal("Pause nach: " + PauseFormat.format(seconds)));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Pause nach: " + PauseFormat.format(seconds())));
        }

        @Override
        protected void applyValue() {
            onSliderChanged(seconds());
        }
    }
}
