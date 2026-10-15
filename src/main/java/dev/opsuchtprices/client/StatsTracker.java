package dev.opsuchtprices.client;

import dev.opsuchtprices.job.LifetimeStats;
import dev.opsuchtprices.job.WorldFilter;
import dev.opsuchtprices.overlay.SlotInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Dauerhafte Statistik: Bäume, Diamanterze, Spawner. Gezählt wird nur auf Farmwelt 1–12 und Nether 1–2
 * (erkannt über das Scoreboard "Server: FARMWELT 1", ersatzweise über "Verbinde zu …" im Chat).
 */
public final class StatsTracker {
    private StatsTracker() {}

    private static LifetimeStats stats;
    private static volatile String world;
    private static volatile String chatWorld;
    private static int tick;

    public static void init(LifetimeStats s) {
        stats = s;
    }

    public static LifetimeStats stats() {
        return stats;
    }

    /** Aktuell erkannte Welt oder null. */
    public static String world() {
        return world != null ? world : chatWorld;
    }

    public static boolean countingAllowed() {
        return WorldFilter.allowed(world());
    }

    /** Jeden Tick: Scoreboard auswerten (alle 20 Ticks). */
    public static void tick(Minecraft mc) {
        if (++tick % 20 != 0) return;
        if (mc.getConnection() == null) {
            // vom Server getrennt
            world = null;
            chatWorld = null;
            return;
        }
        if (mc.level == null) return; // Weltwechsel läuft
        // Scoreboard hat Vorrang; ohne Treffer gilt das letzte "Verbinde zu …"
        world = fromScoreboard(mc.level.getScoreboard());
        if (stats != null && tick % 200 == 0) stats.saveIfDirty();
    }

    /** Chat-Nachricht "Verbinde zu FARMWELT 1..." als Ersatz, falls das Scoreboard nichts liefert. */
    public static void onChat(String text) {
        String target = WorldFilter.connectTarget(SlotInfo.strip(text));
        if (target != null) chatWorld = WorldFilter.find(target) != null ? WorldFilter.find(target) : target;
    }

    private static String fromScoreboard(Scoreboard sb) {
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return null;
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
            String line;
            if (e.display() != null) {
                line = e.display().getString();
            } else {
                PlayerTeam team = sb.getPlayersTeam(e.owner());
                line = PlayerTeam.formatNameForTeam(team, net.minecraft.network.chat.Component.literal(e.owner())).getString();
            }
            String w = WorldFilter.find(line);
            if (w != null) return w;
        }
        return null;
    }

    /** Block wurde vom Spieler abgebaut (Client-Ereignis). */
    public static void onBlockBroken(BlockState state) {
        if (stats == null || !countingAllowed()) return;
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) stats.addDiamondOre();
        else if (state.is(Blocks.SPAWNER)) stats.addSpawner();
    }

    /** Ein Baum wurde erkannt (TreeCounter). */
    public static void onTree() {
        if (stats != null && countingAllowed()) stats.addTree();
    }
}
