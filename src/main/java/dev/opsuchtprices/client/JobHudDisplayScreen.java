package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Einstellungen → Jobtracker-Anzeige: welche Zeilen der Jobtracker zeigt. */
public final class JobHudDisplayScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    private final Runnable save;

    /** Eine Zeile des Jobtrackers mit Lese- und Schreibzugriff auf die Config. */
    record Line(String label, Predicate<ModConfig> get, BiConsumer<ModConfig, Boolean> set) {}

    static final List<Line> LINES = List.of(
            new Line("Fortschrittsbalken", c -> c.jobShowProgress, (c, v) -> c.jobShowProgress = v),
            new Line("Zeit", c -> c.jobShowTime, (c, v) -> c.jobShowTime = v),
            new Line("Geld", c -> c.jobShowMoney, (c, v) -> c.jobShowMoney = v),
            new Line("Geld/h", c -> c.jobShowMoneyPerHour, (c, v) -> c.jobShowMoneyPerHour = v),
            new Line("XP", c -> c.jobShowXp, (c, v) -> c.jobShowXp = v),
            new Line("XP/h", c -> c.jobShowXpPerHour, (c, v) -> c.jobShowXpPerHour = v),
            new Line("Zeit bis Level-Up", c -> c.jobShowLevelUp, (c, v) -> c.jobShowLevelUp = v),
            new Line("Shards (Inventar)", c -> c.jobShowShards, (c, v) -> c.jobShowShards = v),
            new Line("Jobschatz", c -> c.jobShowTreasure, (c, v) -> c.jobShowTreasure = v));

    public JobHudDisplayScreen(Screen parent, ModConfig config, Runnable save) {
        super(Component.literal("Jobtracker – Anzeige"));
        this.parent = parent;
        this.config = config;
        this.save = save;
    }

    private static Component text(Line l, ModConfig c) {
        return Component.literal(l.label() + ": " + (l.get().test(c) ? "AN" : "AUS"));
    }

    @Override
    protected void init() {
        int cx = width / 2, y = 40;
        for (int i = 0; i < LINES.size(); i++) {
            Line l = LINES.get(i);
            int x = i % 2 == 0 ? cx - 155 : cx + 5;
            addRenderableWidget(Button.builder(text(l, config), b -> {
                l.set().accept(config, !l.get().test(config));
                b.setMessage(text(l, config));
            }).bounds(x, y + (i / 2) * 24, 150, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Jobs & Hintergrund…"),
                b -> minecraft.setScreen(MoreSettings.jobs(this, config, save))).bounds(cx + 5, y + 4 * 24, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 100, height - 28, 200, 20).build());
    }

    /** Für Tests: Zeile umschalten wie per Klick. */
    public void toggle(String label) {
        for (Line l : LINES) if (l.label().equals(label)) l.set().accept(config, !l.get().test(config));
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, "Jobtracker – Anzeige", width / 2, 14, 0xFFFFC94A);
        g.drawCenteredString(font, "Welche Zeilen der Jobtracker zeigt", width / 2, 26, 0xFFA8A8A8);
    }

    @Override
    public void onClose() {
        save.run();
        minecraft.setScreen(parent);
    }
}
