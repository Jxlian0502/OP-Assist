package dev.opsuchtprices;

import dev.opsuchtprices.util.NumberShorthand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Kurzschreibweise 1k / 2,5m / 1mrd für /pay, /bank und Amboss. */
class NumberShorthandTest {
    @Test
    void expandsShorthand() {
        assertEquals("1000", NumberShorthand.expand("1k"));
        assertEquals("1500", NumberShorthand.expand("1,5k"));
        assertEquals("2500000", NumberShorthand.expand("2.5m"));
        assertEquals("2500000", NumberShorthand.expand("2,5Mio"));
        assertEquals("3000000000", NumberShorthand.expand("3mrd"));
        assertEquals("1000000000", NumberShorthand.expand("1b"));
        assertEquals("1234.5", NumberShorthand.expand("1,2345k"));
        assertNull(NumberShorthand.expand("1000"), "ohne Einheit unverändert");
        assertNull(NumberShorthand.expand("kiste"));
        assertNull(NumberShorthand.expand("Max"));
        assertNull(NumberShorthand.expand("1k2"));
    }

    @Test
    void onlyPayAndBankCommands() {
        assertEquals("pay Max 1500", NumberShorthand.expandCommand("pay Max 1,5k"));
        assertEquals("bank einzahlen 2000000", NumberShorthand.expandCommand("bank einzahlen 2m"));
        assertEquals("PAY Max 1000", NumberShorthand.expandCommand("PAY Max 1k"));
        assertEquals("msg Max 1k", NumberShorthand.expandCommand("msg Max 1k"), "andere Befehle bleiben");
        assertEquals("pay Kevin1k 5", NumberShorthand.expandCommand("pay Kevin1k 5"), "Spielernamen bleiben");
        assertEquals("pay", NumberShorthand.expandCommand("pay"));
    }
}
