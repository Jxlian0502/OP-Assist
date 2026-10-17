package dev.opsuchtprices;

import dev.opsuchtprices.util.SpeedMath;
import dev.opsuchtprices.util.SpeedMath.Mod;
import dev.opsuchtprices.util.SpeedMath.Op;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Speed-Bonus aus Item-Attributen, gleiche Formel wie Minecraft. */
class SpeedMathTest {
    static final double BASE = 0.1; // Grundgeschwindigkeit eines Spielers

    @Test
    void noItemsIsZero() {
        assertEquals(0, SpeedMath.percent(BASE, List.of()), 1e-9);
        assertEquals("+0%", SpeedMath.format(0));
    }

    @Test
    void percentOnBaseAddsUp() {
        // Stiefel +50 %, Axt in der Hand +30 % -> +80 %
        assertEquals(80, SpeedMath.percent(BASE, List.of(new Mod(Op.ADD_MULTIPLIED_BASE, 0.5),
                new Mod(Op.ADD_MULTIPLIED_BASE, 0.3))), 1e-9);
    }

    @Test
    void minecraftOrderOfOperations() {
        // +0,05 fest (=> 0,15), +100 % der Basis (=> 0,30), dann x1,1 (=> 0,33) => +230 %
        double p = SpeedMath.percent(BASE, List.of(new Mod(Op.ADD_MULTIPLIED_TOTAL, 0.1),
                new Mod(Op.ADD_VALUE, 0.05), new Mod(Op.ADD_MULTIPLIED_BASE, 1.0)));
        assertEquals(230, p, 1e-9);
        assertEquals("+230%", SpeedMath.format(p));
    }

    @Test
    void shardButtonUsesMerchantAndOldConfigIsMigrated() throws Exception {
        var cfg = new dev.opsuchtprices.config.ModConfig();
        assertEquals(1, cfg.inventoryButtonList.stream().filter(b -> b.command.equals("merchant")).count());
        java.nio.file.Path f = java.nio.file.Files.createTempFile("cfg", ".json");
        java.nio.file.Files.writeString(f, "{\"inventoryButtonList\":[{\"item\":\"minecraft:amethyst_shard\",\"label\":\"Shards\",\"command\":\"shards\"}]}");
        var loaded = dev.opsuchtprices.config.ModConfig.load(f);
        assertEquals("merchant", loaded.inventoryButtonList.get(0).command);
        assertEquals("Shardhändler", loaded.inventoryButtonList.get(0).label);
        java.nio.file.Files.deleteIfExists(f);
    }

    /** Echte Messung (17.10.): Speed Boots II (+0,1 fest), Bergarbeiter Helm (+25 %), Schnelligkeit II, OPMOD zeigt +230 %. */
    @Test
    void matchesOpmodMeasurement() {
        double items = SpeedMath.percent(BASE, List.of(new Mod(Op.ADD_VALUE, 0.1), new Mod(Op.ADD_MULTIPLIED_BASE, 0.25)));
        assertEquals(150, items, 1e-9);
        int mult = SpeedMath.effectMultiplier(List.of("➜ Effekt: x2 Speed Effekt", "§7Andere Zeile"));
        assertEquals(2, mult);
        assertEquals("+230%", SpeedMath.format(SpeedMath.opSuchtPercent(items, 2, mult)));
        // ohne Effekt bleibt es beim Item-Speed, ohne x2-Item zählt der Effekt einfach
        assertEquals("+150%", SpeedMath.format(SpeedMath.opSuchtPercent(items, 0, mult)));
        assertEquals("+190%", SpeedMath.format(SpeedMath.opSuchtPercent(items, 2, 1)));
        assertEquals(1, SpeedMath.effectMultiplier(List.of("➥ Effekt: +25% Geschwindigkeit (Kopf)")));
    }

    @Test
    void slownessIsNegative() {
        assertEquals("-15%", SpeedMath.format(SpeedMath.percent(BASE, List.of(new Mod(Op.ADD_MULTIPLIED_TOTAL, -0.15)))));
    }
}
