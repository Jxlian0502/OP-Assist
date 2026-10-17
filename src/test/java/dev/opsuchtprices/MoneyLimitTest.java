package dev.opsuchtprices;

import dev.opsuchtprices.job.MoneyLimit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Job-Geld-Limit: Erkennen der Server-Meldung, nur einmal Bescheid geben, Meldung wenn vorbei. */
class MoneyLimitTest {
    /** Echter Wortlaut vom Server (Screenshot), mit Farbcodes. */
    static final String MSG = "§4OPSUCHT §8» §cDu hast dein Job-Geld-Limit erreicht, weiter geht es in 22 Minuten!";

    @Test
    void recognizesServerMessageAndWaitTime() {
        assertTrue(MoneyLimit.isLimitMessage(MSG));
        assertEquals(22 * 60_000L, MoneyLimit.waitMillis(MSG));
        assertEquals(60_000L, MoneyLimit.waitMillis("Du hast dein Job-Geld-Limit erreicht, weiter geht es in 1 Minute!"));
        assertEquals(30_000L, MoneyLimit.waitMillis("Du hast dein Job-Geld-Limit erreicht, weiter geht es in 30 Sekunden!"));
        assertEquals(-1, MoneyLimit.waitMillis("Du hast dein Job-Geld-Limit erreicht!"));
        assertFalse(MoneyLimit.isLimitMessage("Du hast einen Jobschatz erhalten!"));
        assertFalse(MoneyLimit.isLimitMessage("Spieler » mein Job-Geld ist hoch"));
    }

    @Test
    void notifiesOnceThenWhenOver() {
        MoneyLimit l = new MoneyLimit();
        long t = 1_000_000;
        assertTrue(l.onLimitMessage(MSG, t), "erste Meldung -> Bescheid geben");
        assertEquals(t + 22 * 60_000L, l.resetAt());
        // Server wiederholt die Meldung bei jeder Job-Aktion: kein Spam
        assertFalse(l.onLimitMessage(MSG.replace("22", "21"), t + 60_000));
        assertFalse(l.onLimitMessage(MSG.replace("22", "5"), t + 17 * 60_000));
        assertFalse(l.pollReset(t + 21 * 60_000), "noch nicht vorbei");
        assertTrue(l.pollReset(t + 22 * 60_000 + 1), "vorbei -> einmal Bescheid");
        assertFalse(l.pollReset(t + 23 * 60_000), "nur einmal");
        assertEquals(0, l.resetAt());
        // Nächste Stunde: neues Limit -> wieder Bescheid
        assertTrue(l.onLimitMessage(MSG, t + 90 * 60_000));
    }

    @Test
    void messageWithoutTimeNotifiesButNoReminder() {
        MoneyLimit l = new MoneyLimit();
        assertTrue(l.onLimitMessage("Du hast dein Job-Geld-Limit erreicht!", 1000));
        assertEquals(0, l.resetAt());
        assertFalse(l.onLimitMessage("Du hast dein Job-Geld-Limit erreicht!", 5 * 60_000), "kein Spam ohne Zeitangabe");
        assertTrue(l.onLimitMessage("Du hast dein Job-Geld-Limit erreicht!", 20 * 60_000), "nach 10 Min. wieder");
        assertFalse(l.pollReset(Long.MAX_VALUE));
    }
}
