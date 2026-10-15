package dev.opsuchtprices;

import dev.opsuchtprices.chat.ChatRepeatTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatRepeatTrackerTest {

    static final long MIN = 60_000;

    @Test
    void trenntAbsenderUndText() {
        var p = ChatRepeatTracker.split("§7[VIP] §bMax §8» §fVerkaufe Diamantblock 700$");
        assertEquals("[VIP] Max", p.sender());
        assertEquals("Verkaufe Diamantblock 700$", p.body());
        assertEquals("Hallo Leute", ChatRepeatTracker.split("<Steve>: Hallo Leute").body());
        assertEquals("ohne Absender", ChatRepeatTracker.split("ohne Absender").body());
        var v = ChatRepeatTracker.split("<Steve> Hallo zusammen");
        assertEquals("Steve", v.sender());
        assertEquals("Hallo zusammen", v.body());
    }

    @Test
    void opsuchtChatformatMitUhrzeitUndCopy() {
        var p = ChatRepeatTracker.split("20:15:39 » Spieler | LxonYT » hi Leute bin Live auf YT kommt gerne vorbei Name Kartoffelcraft » [Copy]");
        assertEquals("Spieler | LxonYT", p.sender());
        assertEquals("hi Leute bin Live auf YT kommt gerne vorbei Name Kartoffelcraft", p.body());
        assertEquals("lxonyt", ChatRepeatTracker.playerName(p.sender()));
        assertEquals("OPSUCHT", ChatRepeatTracker.split("20:17:59 » OPSUCHT » Du hast einen Jobschatz erhalten! [Copy]").sender());
        var q = ChatRepeatTracker.split("20:15:38 » »\nPLATIN | ~LittleJekko11 » Wale*\n» [Copy]");
        assertEquals("littlejekko11", ChatRepeatTracker.playerName(q.sender()));
        assertEquals("Wale*", q.body());
        // gleiche Nachricht, andere Uhrzeit -> Wiederholung
        ChatRepeatTracker t = new ChatRepeatTracker(15 * MIN, 5);
        assertNull(t.record("20:15:39 » Spieler | LxonYT » hi Leute bin Live auf YT » [Copy]", 0));
        assertNotNull(t.record("20:19:02 » Spieler | LxonYT » hi Leute bin Live auf YT » [Copy]", 4 * MIN));
        assertNull(t.record("20:19:05 » OP | watchinger » hi Leute bin Live auf YT » [Copy]", 4 * MIN));
    }

    @Test
    void erkenntWiederholungIn15Minuten() {
        ChatRepeatTracker t = new ChatRepeatTracker(15 * MIN, 5);
        assertNull(t.record("Max » Verkaufe Diamantblock 700$", 0));
        // anderer Spieler, gleicher Text -> kein Treffer
        assertNull(t.record("Tim » Verkaufe Diamantblock 700$", 2 * MIN));
        // gleicher Spieler (auch mit anderem Rang), andere Schreibweise -> Treffer
        ChatRepeatTracker.Hit h = t.record("[VIP] Max » verkaufe  DIAMANTBLOCK 700$", 5 * MIN);
        assertNotNull(h);
        assertEquals(2, h.count());
        assertEquals("Max", h.firstSender());
        assertEquals("max", ChatRepeatTracker.playerName("§7[VIP] Max"));
        assertEquals(0, h.firstAt());
        h = t.record("Max » Verkaufe Diamantblock 700$", 14 * MIN);
        assertEquals(3, h.count());
        assertEquals(5 * MIN, h.previousAt());
        // nach mehr als 15 Min. seit der letzten Meldung: alte fallen raus
        h = t.record("Max » Verkaufe Diamantblock 700$", 30 * MIN);
        assertNull(h, "letzte Meldung vor 16 Min. -> keine Wiederholung");
        // kurze Nachrichten werden ignoriert
        assertNull(t.record("Max » hi", 30 * MIN));
        assertNull(t.record("Max » hi", 31 * MIN));
    }
}
