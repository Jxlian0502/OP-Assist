package dev.opsuchtprices;

import dev.opsuchtprices.api.PriceApiJson;
import dev.opsuchtprices.cache.SalesStore;
import dev.opsuchtprices.model.SaleRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Datenformat der Preis-Schnittstelle und Befüllen des Zwischenspeichers. */
class PriceApiJsonTest {

    @Test
    void roundTripKeepsAllFields() {
        SaleRecord a = new SaleRecord("u1", "PAPER|holzbündel|", 179000, 64, Instant.parse("2026-10-16T20:00:00.123Z"),
                "INSTANT_BUY", "Holzbündel (PAPER)", "https://items.opsucht.net/x.png");
        SaleRecord b = new SaleRecord("u2", "DIAMOND_SWORD||minecraft:sharpness=5", 1000, 1,
                Instant.parse("2026-10-16T21:00:00Z"), "AUCTION_SOLD");
        PriceApiJson.Result r = PriceApiJson.parse(PriceApiJson.write(List.of(a, b), 42));
        assertEquals(42, r.generatedAt());
        assertEquals(List.of(a, b), r.sales(), "alle Felder inkl. Umlaute, Millisekunden, fehlendes Label/Icon");
    }

    @Test
    void brokenEntriesAreSkipped() {
        String json = "{\"v\":1,\"generatedAt\":1,\"sales\":[{\"u\":\"a\",\"k\":\"K||\",\"p\":5,\"a\":2,\"t\":1000,\"y\":\"INSTANT_BUY\"},"
                + "{\"u\":\"b\"},{\"u\":\"c\",\"k\":\"K||\",\"p\":0,\"a\":1,\"t\":1000},\"x\"]}";
        List<SaleRecord> s = PriceApiJson.parse(json).sales();
        assertEquals(1, s.size());
        assertEquals(2.5, s.get(0).pricePerItem(), 1e-9);
    }

    @Test
    void replaceAllFillsCacheAndKeepsNewestPerItem() {
        SalesStore store = new SalesStore(2);
        store.record(new SaleRecord("alt", "X||", 1, 1, Instant.parse("2026-01-01T00:00:00Z"), "INSTANT_BUY"));
        store.replaceAll(List.of(
                new SaleRecord("a", "K||", 100, 1, Instant.parse("2026-10-16T10:00:00Z"), "INSTANT_BUY"),
                new SaleRecord("c", "K||", 300, 1, Instant.parse("2026-10-16T12:00:00Z"), "AUCTION_SOLD"),
                new SaleRecord("b", "K||", 200, 1, Instant.parse("2026-10-16T11:00:00Z"), "INSTANT_BUY")));
        assertTrue(store.summary("X||").isEmpty(), "alter Inhalt ersetzt");
        SalesStore.Summary s = store.summary("K||").orElseThrow();
        assertEquals(2, s.count());
        assertEquals("c", s.last().uid());
        assertEquals(250.0, s.avgPerItem(), 1e-9);
        assertFalse(store.record(new SaleRecord("c", "K||", 300, 1, Instant.parse("2026-10-16T12:00:00Z"), "AUCTION_SOLD")),
                "beim Nachholen doppelt geladene Verkäufe werden erkannt");
    }

    @Test
    void defaultServerIsSetForEveryone() {
        assertEquals("http://217.160.209.120", new dev.opsuchtprices.config.ModConfig().priceServerUrl);
    }
}
