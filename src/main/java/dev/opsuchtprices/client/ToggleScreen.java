package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Einfache Einstellungsseite: Liste aus An/Aus-Schaltern und Schiebereglern in zwei Spalten.
 * Wird für "Weitere Funktionen" und "Jobs & Hintergrund" verwendet.
 */
public class ToggleScreen extends Screen {
    public interface Entry {}

    public record Toggle(String label, Predicate<ModConfig> get, BiConsumer<ModConfig, Boolean> set) implements Entry {}

    public record Slider(String label, int min, int max, ToIntFunction<ModConfig> get, BiConsumer<ModConfig, Integer> set,
                         IntFunction<String> format) implements Entry {}

    /** Knopf, der eine weitere Seite öffnet. */
    public record Link(String label, Function<Screen, Screen> open) implements Entry {}

    private final Screen parent;
    private final ModConfig config;
    private final Runnable save;
    private final String heading;
    private final String sub;
    private final List<Entry> entries;

    public ToggleScreen(Screen parent, ModConfig config, Runnable save, String heading, String sub, List<Entry> entries) {
        super(Component.literal(heading));
        this.parent = parent;
        this.config = config;
        this.save = save;
        this.heading = heading;
        this.sub = sub;
        this.entries = new ArrayList<>(entries);
    }

    private static Component text(Toggle t, ModConfig c) {
        return Component.literal(t.label() + ": " + (t.get().test(c) ? "AN" : "AUS"));
    }

    @Override
    protected void init() {
        int cx = width / 2, y = 40;
        for (int i = 0; i < entries.size(); i++) {
            int x = i % 2 == 0 ? cx - 155 : cx + 5, by = y + (i / 2) * 24;
            Entry e = entries.get(i);
            if (e instanceof Toggle t) {
                addRenderableWidget(Button.builder(text(t, config), b -> {
                    t.set().accept(config, !t.get().test(config));
                    b.setMessage(text(t, config));
                }).bounds(x, by, 150, 20).build());
            } else if (e instanceof Slider s) {
                addRenderableWidget(new IntSlider(x, by, s));
            } else if (e instanceof Link l) {
                addRenderableWidget(Button.builder(Component.literal(l.label()), b -> minecraft.setScreen(l.open().apply(this)))
                        .bounds(x, by, 150, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 100, height - 28, 200, 20).build());
    }

    /** Für Tests: Schalter umschalten wie per Klick. */
    public void toggle(String label) {
        for (Entry e : entries) {
            if (e instanceof Toggle t && t.label().equals(label)) t.set().accept(config, !t.get().test(config));
        }
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, heading, width / 2, 14, 0xFFFFC94A);
        g.drawCenteredString(font, sub, width / 2, 26, 0xFFA8A8A8);
    }

    @Override
    public void onClose() {
        save.run();
        minecraft.setScreen(parent);
    }

    private final class IntSlider extends AbstractSliderButton {
        private final Slider s;

        IntSlider(int x, int y, Slider s) {
            super(x, y, 150, 20, Component.empty(), (s.get().applyAsInt(config) - s.min()) / (double) (s.max() - s.min()));
            this.s = s;
            updateMessage();
        }

        private int current() {
            return s.min() + (int) Math.round(value * (s.max() - s.min()));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(s.label() + ": " + s.format().apply(current())));
        }

        @Override
        protected void applyValue() {
            s.set().accept(config, current());
        }
    }
}
