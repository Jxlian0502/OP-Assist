package dev.opsuchtprices;

import dev.opsuchtprices.job.JobParser;
import dev.opsuchtprices.job.JobTracker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class JobTrackerTest {

    static final String BAR = "§e+2,75 XP §8• §a+15,887$ §8• §bHolzfäller §7Level 78 §8• §8[§a||||||||||||§7||||||||§8] §756,79%";

    @Test
    void parstActionbarAusDemScreenshot() {
        JobParser.JobTick t = JobParser.parse(BAR);
        assertNotNull(t);
        assertEquals("Holzfäller", t.job());
        assertEquals(78, t.level());
        assertEquals(2.75, t.xp(), 1e-9);
        assertEquals(15.887, t.money(), 1e-9);
        assertEquals(56.79, t.percent(), 1e-9);
    }

    @Test
    void tausenderUndAndereJobs() {
        JobParser.JobTick t = JobParser.parse("+1.234,5 XP • +2.000$ • Minenarbeiter Level 120 • [||] 3,1%");
        assertEquals("Minenarbeiter", t.job());
        assertEquals(1234.5, t.xp(), 1e-9);
        assertEquals(2000.0, t.money(), 1e-9);
        assertEquals(3.1, t.percent(), 1e-9);
        assertNull(JobParser.parse("Du hast 5$ erhalten"));
        assertNull(JobParser.parse("OPSUCHT » Level 5 erreicht"));
    }

    @Test
    void zaehltSummenZeitUndLevelUp() {
        AtomicLong now = new AtomicLong(1_000_000);
        JobTracker tr = new JobTracker(now::get, 60_000);
        assertTrue(tr.onActionBar("+2 XP • +10$ • Holzfäller Level 78 • [] 50,00%"));
        // Wiederholung derselben Anzeige zählt (wie OPMOD: eine Meldung pro Block)
        now.addAndGet(1000);
        tr.onActionBar("+2 XP • +10$ • Holzfäller Level 78 • [] 50,00%");
        JobTracker.Stats s = tr.current();
        assertEquals(2, s.actions);
        // 10 Belohnungen im Abstand von 6 s, je +1 %
        for (int i = 1; i <= 10; i++) {
            now.addAndGet(6000);
            tr.onActionBar("+2 XP • +10$ • Holzfäller Level 78 • [] " + (50 + i) + ",00%");
        }
        assertEquals(12, s.actions);
        assertEquals(24.0, s.xp, 1e-9);
        assertEquals(120.0, s.money, 1e-9);
        assertEquals(61_000, tr.activeMs(s));
        assertEquals(120.0 * 3600_000 / 61_000, tr.perHour(s, s.money), 1e-6);
        assertEquals(244_000, tr.timeToLevelUpMs(s), 100); // 50 -> 60 % in 61 s, Rest 40 %
        // Pause: 2 Minuten nichts -> Zeit läuft nicht weiter
        now.addAndGet(120_000);
        assertTrue(tr.isPaused(s));
        assertEquals(61_000, tr.activeMs(s));
        tr.onActionBar("+2 XP • +10$ • Holzfäller Level 79 • [] 0,50%");
        assertEquals(61_000, tr.activeMs(s), "Lücke nach Pause zählt nicht");
        assertEquals(1, s.levelUps);
        assertNull(tr.timeToLevelUpMs(s), "neues Level: noch keine Schätzung");
        // anderer Job
        tr.onActionBar("+1 XP • +1$ • Fischer Level 3 • [] 1,00%");
        assertEquals("Fischer", tr.current().job);
        assertEquals(2, tr.all().size());
        tr.reset();
        assertNull(tr.current());
    }

    @Test
    void xpZaehlerBeginntBei100000Neu(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        java.nio.file.Path f = dir.resolve("job-zaehler.json");
        dev.opsuchtprices.job.XpCounter c = dev.opsuchtprices.job.XpCounter.load(f, 100_000);
        assertEquals(0, c.add(99_999.5));
        assertEquals(1, c.add(2.75));
        assertEquals(1, c.rounds());
        assertEquals(2.25, c.current(), 1e-9);
        c.saveIfDirty();
        dev.opsuchtprices.job.XpCounter again = dev.opsuchtprices.job.XpCounter.load(f, 100_000);
        assertEquals(1, again.rounds(), "bleibt nach Neustart erhalten");
        assertEquals(2.25, again.current(), 1e-9);
        assertEquals(2, again.add(200_000));
        assertEquals(3, again.rounds());
        // Tracker meldet nur gezählte (nicht doppelte) Belohnungen an den Zähler
        AtomicLong now = new AtomicLong(0);
        JobTracker tr = new JobTracker(now::get, 60_000);
        double[] sum = {0};
        tr.setXpListener(xp -> sum[0] += xp);
        tr.onActionBar(BAR);
        tr.onActionBar(BAR);
        assertEquals(5.5, sum[0], 1e-9, "jede Meldung zählt");
    }

    @Test
    void jobschatzSetztZaehlerZurueckUndZaehltFunde(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        java.nio.file.Path f = dir.resolve("job-zaehler.json");
        var c = dev.opsuchtprices.job.XpCounter.load(f, 100_000);
        c.add(1102);
        assertTrue(dev.opsuchtprices.job.XpCounter.isTreasureMessage("20:17:59 » §cOPSUCHT §8» §7Du hast einen §bJobschatz §7erhalten!"));
        assertFalse(dev.opsuchtprices.job.XpCounter.isTreasureMessage("Spieler | Max » Jobschatz ist toll"));
        assertTrue(c.treasureFound(1_000_000));
        assertFalse(c.treasureFound(1_002_000), "gleiche Meldung kurz danach zählt nicht doppelt");
        assertEquals(0, c.current(), 1e-9);
        assertEquals(1, c.found());
        c.add(500);
        c.reset();
        assertEquals(1, c.found(), "Reset behält die Funde");
        assertEquals(0, c.current(), 1e-9);
        assertTrue(c.treasureFound(2_000_000));
        c.saveIfDirty();
        assertEquals(2, dev.opsuchtprices.job.XpCounter.load(f, 100_000).found(), "bleibt nach Neustart");
    }

    @Test
    void baeumeZaehlenDauerhaft(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        var tc = new dev.opsuchtprices.job.TreeCounter();
        // Baumfäller-Axt: Anschlagen, dann 9 Meldungen -> 1 Baum
        tc.onLogAttack(0);
        int trees = 0;
        for (int i = 0; i < 9; i++) if (tc.onHolzfaellerBar(10 + i)) trees++;
        assertEquals(1, trees);
        // normale Axt: pro Anschlagen nur eine Meldung -> kein Baum
        for (int i = 0; i < 5; i++) {
            tc.onLogAttack(10_000 + i * 1000);
            if (tc.onHolzfaellerBar(10_100 + i * 1000)) trees++;
        }
        assertEquals(1, trees);
        // Meldungen ohne vorheriges Anschlagen (zu spät) zählen nicht
        tc.onLogAttack(50_000);
        assertFalse(tc.onHolzfaellerBar(60_000));

        java.nio.file.Path f = dir.resolve("job-zaehler.json");
        var c = dev.opsuchtprices.job.XpCounter.load(f, 100_000);
        c.addTree();
        c.addTree();
        c.reset();
        c.treasureFound(1);
        assertEquals(2, c.trees(), "Reset und Jobschatz behalten die Bäume");
        c.saveIfDirty();
        assertEquals(2, dev.opsuchtprices.job.XpCounter.load(f, 100_000).trees(), "bleibt nach Neustart");
    }

    @Test
    void weltFilterUndStatistik(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        assertEquals("Farmwelt 1", dev.opsuchtprices.job.WorldFilter.find("FARMWELT 1"));
        assertEquals("Nether 2", dev.opsuchtprices.job.WorldFilter.find("§fNETHER 2"));
        assertNull(dev.opsuchtprices.job.WorldFilter.find("REDSTONE"));
        assertTrue(dev.opsuchtprices.job.WorldFilter.allowed("Farmwelt 12"));
        assertFalse(dev.opsuchtprices.job.WorldFilter.allowed("Farmwelt 13"));
        assertTrue(dev.opsuchtprices.job.WorldFilter.allowed("Nether 1"));
        assertFalse(dev.opsuchtprices.job.WorldFilter.allowed("Nether 3"));
        assertFalse(dev.opsuchtprices.job.WorldFilter.allowed("REDSTONE"));
        assertFalse(dev.opsuchtprices.job.WorldFilter.allowed(null));
        assertEquals("FARMWELT 1", dev.opsuchtprices.job.WorldFilter.connectTarget("21:16:07 » OPSUCHT » Verbinde zu FARMWELT 1... [Copy]"));
        assertEquals("CB2", dev.opsuchtprices.job.WorldFilter.connectTarget("OPSUCHT » Verbinde zu CB2..."));

        java.nio.file.Path f = dir.resolve("statistik.json");
        var st = dev.opsuchtprices.job.LifetimeStats.load(f);
        st.migrateTrees(31);
        st.addTree();
        st.addDiamondOre();
        st.addSpawner();
        st.saveIfDirty();
        var again = dev.opsuchtprices.job.LifetimeStats.load(f);
        assertEquals(32, again.trees());
        assertEquals(1, again.diamondOres());
        assertEquals(1, again.spawners());
        again.migrateTrees(500);
        assertEquals(32, again.trees(), "Übernahme nur einmal");
    }
}
