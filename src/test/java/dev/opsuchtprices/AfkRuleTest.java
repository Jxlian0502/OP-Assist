package dev.opsuchtprices;

import dev.opsuchtprices.util.AfkRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** AFK-Warnung nur nach einem Job, nicht beim normalen Herumstehen. */
class AfkRuleTest {
    static final long MIN = 60_000L, AFK = 4 * MIN;

    @Test
    void warnsWhenJobStoppedAndPlayerStoodStill() {
        long now = 100 * MIN, lastMove = now - 5 * MIN;
        // letzte Job-Meldung 30 s vor dem Stillstand
        assertTrue(AfkRule.shouldWarn(now, lastMove, 5 * MIN + 30_000, AFK));
    }

    @Test
    void noWarningWithoutJob() {
        long now = 100 * MIN, lastMove = now - 10 * MIN;
        assertFalse(AfkRule.shouldWarn(now, lastMove, null, AFK), "nie einen Job gemacht");
        assertFalse(AfkRule.shouldWarn(now, lastMove, 60 * MIN, AFK), "Job war lange vor dem Stillstand (z. B. im Citybuild herumstehen)");
    }

    @Test
    void noWarningWhileJobStillRuns() {
        long now = 100 * MIN, lastMove = now - 10 * MIN;
        assertFalse(AfkRule.shouldWarn(now, lastMove, 20_000L, AFK), "angeln im Stehen: Job-Meldungen kommen noch");
    }

    @Test
    void noWarningBeforeTimeIsUp() {
        long now = 100 * MIN, lastMove = now - 3 * MIN;
        assertFalse(AfkRule.shouldWarn(now, lastMove, 3 * MIN, AFK));
    }
}
