package dev.opsuchtprices;

import dev.opsuchtprices.job.PauseFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PauseFormatTest {
    @Test
    void eingabenLesen() {
        assertEquals(45, PauseFormat.parse("45"));
        assertEquals(45, PauseFormat.parse("45s"));
        assertEquals(45, PauseFormat.parse("45 Sekunden"));
        assertEquals(90, PauseFormat.parse("1:30"));
        assertEquals(120, PauseFormat.parse("2m"));
        assertEquals(120, PauseFormat.parse("2 min"));
        assertEquals(90, PauseFormat.parse("1m30s"));
        assertEquals(1, PauseFormat.parse("1"));
        assertEquals(-1, PauseFormat.parse("0"));
        assertEquals(-1, PauseFormat.parse("abc"));
        assertEquals(-1, PauseFormat.parse(""));
        assertEquals(-1, PauseFormat.parse("61:00"));
    }

    @Test
    void anzeigen() {
        assertEquals("45 s", PauseFormat.format(45));
        assertEquals("1:30 Min.", PauseFormat.format(90));
        assertEquals("5:00 Min.", PauseFormat.format(300));
        assertEquals("1:30", PauseFormat.formatInput(90));
        assertEquals("45s", PauseFormat.formatInput(45));
    }
}
