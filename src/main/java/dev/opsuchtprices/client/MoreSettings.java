package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Einstellungsseiten "Weitere Funktionen" und "Jobtracker – Jobs & Hintergrund". */
public final class MoreSettings {
    private MoreSettings() {}

    public static Screen features(Screen parent, ModConfig config, Runnable save) {
        return new ToggleScreen(parent, config, save, "Weitere Funktionen", "Warnungen, Schutz und Komfort", List.of(
                new ToggleScreen.Toggle("Inventar voll", c -> c.invFullWarning, (c, v) -> c.invFullWarning = v),
                new ToggleScreen.Toggle("…mit Ton", c -> c.invFullWarningSound, (c, v) -> c.invFullWarningSound = v),
                new ToggleScreen.Toggle("AFK nach Job", c -> c.afkWarning, (c, v) -> c.afkWarning = v),
                new ToggleScreen.Toggle("…mit Ton", c -> c.afkWarningSound, (c, v) -> c.afkWarningSound = v),
                new ToggleScreen.Slider("AFK nach", 1, 15, c -> c.afkWarningMinutes, (c, v) -> c.afkWarningMinutes = v, v -> v + " Min."),
                new ToggleScreen.Toggle("Kurzschreibweise 1k", c -> c.numberShorthand, (c, v) -> c.numberShorthand = v),
                new ToggleScreen.Toggle("Sign-Schutz /sign", c -> c.signProtection, (c, v) -> c.signProtection = v),
                new ToggleScreen.Toggle("Rename-Schutz /rename", c -> c.renameProtection, (c, v) -> c.renameProtection = v),
                new ToggleScreen.Toggle("Verkaufbares grün", c -> c.highlightSellable, (c, v) -> c.highlightSellable = v),
                new ToggleScreen.Toggle("Rucksack-Vorschau", c -> c.bundlePreview, (c, v) -> c.bundlePreview = v)));
    }

    /** Jobs in der Reihenfolge der Actionbar-Namen (Kleinbuchstaben) und Anzeigenamen. */
    static final String[][] JOBS = {{"minenarbeiter", "Minenarbeiter"}, {"holzfäller", "Holzfäller"}, {"gräber", "Gräber"},
            {"jäger", "Jäger"}, {"fischer", "Fischer"}, {"farmer", "Farmer"}, {"builder", "Builder"}};

    public static Screen jobs(Screen parent, ModConfig config, Runnable save) {
        List<ToggleScreen.Entry> e = new ArrayList<>();
        for (String[] j : JOBS) {
            e.add(new ToggleScreen.Toggle(j[1], c -> c.jobTrackedJobs.contains(j[0]), (c, v) -> {
                c.jobTrackedJobs.remove(j[0]);
                if (v) c.jobTrackedJobs.add(j[0]);
            }));
        }
        e.add(new ToggleScreen.Slider("Hintergrund", 0, 100, c -> c.jobBgOpacity, (c, v) -> c.jobBgOpacity = v, v -> v + " %"));
        e.add(new ToggleScreen.Toggle("Auf Citybuild aus", c -> c.jobHideOnCitybuild, (c, v) -> c.jobHideOnCitybuild = v));
        return new ToggleScreen(parent, config, save, "Jobtracker – Jobs & Hintergrund",
                "Welche Jobs der Jobtracker zeigt", e);
    }
}
