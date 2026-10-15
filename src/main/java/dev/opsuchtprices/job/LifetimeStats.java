package dev.opsuchtprices.job;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.opsuchtprices.util.AtomicFiles;
import dev.opsuchtprices.util.ModLog;

import java.io.IOException;
import java.nio.file.Path;

/** Dauerhafte Statistik (statistik.json): gefällte Bäume, abgebaute Diamanterze, Spawner. Wird nie zurückgesetzt. */
public final class LifetimeStats {

    static final class State {
        long trees;
        long diamondOres;
        long spawners;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private State state = new State();
    private boolean dirty;

    public LifetimeStats(Path file) {
        this.file = file;
    }

    public static LifetimeStats load(Path file) {
        LifetimeStats s = new LifetimeStats(file);
        if (file != null) {
            try {
                String json = AtomicFiles.readStringOrNull(file);
                if (json != null) {
                    State st = GSON.fromJson(json, State.class);
                    if (st != null) s.state = st;
                }
            } catch (IOException | RuntimeException e) {
                ModLog.warn("Statistik {} unlesbar: {}", file, e.toString());
            }
        }
        return s;
    }

    public synchronized void addTree() { state.trees++; dirty = true; }
    public synchronized void addDiamondOre() { state.diamondOres++; dirty = true; }
    public synchronized void addSpawner() { state.spawners++; dirty = true; }
    public synchronized long trees() { return state.trees; }
    public synchronized long diamondOres() { return state.diamondOres; }
    public synchronized long spawners() { return state.spawners; }

    /** Übernahme der Bäume aus einer älteren Version (Jobschatz-Datei), nur wenn hier noch nichts steht. */
    public synchronized void migrateTrees(long trees) {
        if (state.trees == 0 && trees > 0) {
            state.trees = trees;
            dirty = true;
        }
    }

    public synchronized void saveIfDirty() {
        if (!dirty || file == null) return;
        try {
            AtomicFiles.writeString(file, GSON.toJson(state));
            dirty = false;
        } catch (IOException e) {
            ModLog.warn("Statistik konnte nicht gespeichert werden: {}", e.toString());
        }
    }
}
