package dev.opsuchtprices;

import dev.opsuchtprices.market.MarketEntry;
import dev.opsuchtprices.market.MarketHistory;
import dev.opsuchtprices.market.MarketParser;
import dev.opsuchtprices.market.MarketService;
import dev.opsuchtprices.market.MarketStats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Tests mit einer gespeicherten echten Antwort der Markt-API (14./15.10.2026), ohne Netzwerk. */
class MarketTest {

    static String res(String name) throws IOException {
        try (InputStream in = MarketTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static final String SMALL = "{\"Bergbau\":{"
            + "\"DIAMOND_BLOCK\":[{\"orderSide\":\"BUY\",\"activeOrders\":432,\"price\":678.7},{\"orderSide\":\"SELL\",\"activeOrders\":131,\"price\":600.1}],"
            + "\"COAL\":[{\"orderSide\":\"BUY\",\"activeOrders\":3,\"price\":2.5},{\"orderSide\":\"SELL\",\"activeOrders\":0,\"price\":0.0}]},"
            + "\"Besonderes\":{\"BEACON\":[{\"orderSide\":\"BUY\",\"activeOrders\":2,\"price\":3.6E5},{\"orderSide\":\"SELL\",\"activeOrders\":1,\"price\":2.9E5}],"
            + "\"DRAGON_EGG\":[{\"orderSide\":\"BUY\",\"activeOrders\":0,\"price\":0.0},{\"orderSide\":\"SELL\",\"activeOrders\":0,\"price\":0.0}]}}";

    static MarketEntry find(List<MarketEntry> l, String m) {
        return l.stream().filter(e -> e.material().equals(m)).findFirst().orElseThrow();
    }

    @Test
    void echteAntwortWirdVollstaendigGelesen() throws Exception {
        List<MarketEntry> all = MarketParser.parsePrices(res("market-prices.json"));
        assertEquals(444, all.size(), "alle Items, keine Pagination");
        assertEquals(6, all.stream().map(MarketEntry::category).distinct().count());
        assertTrue(all.stream().allMatch(e -> e.buyPrice() == null || e.buyPrice() > 0));
    }

    @Test
    void preiseUndLeereSeiten() throws Exception {
        List<MarketEntry> l = MarketParser.parsePrices(SMALL);
        MarketEntry d = find(l, "DIAMOND_BLOCK");
        assertEquals(678.7, d.buyPrice(), 1e-9);
        assertEquals(600.1, d.sellPrice(), 1e-9);
        assertEquals(563, d.totalOrders());
        assertEquals(78.6, d.spread(), 1e-9);
        assertNull(find(l, "COAL").sellPrice(), "0 Orders = kein Preis");
        assertEquals(360000.0, find(l, "BEACON").buyPrice(), 1e-9, "wissenschaftliche Notation");
        assertFalse(find(l, "DRAGON_EGG").hasAnyOrders());
    }

    @Test
    void kaputteAntworten() {
        assertThrows(IOException.class, () -> MarketParser.parsePrices("<html>"));
        assertThrows(IOException.class, () -> MarketParser.parsePrices("[]"));
        assertDoesNotThrow(() -> MarketParser.parsePrices("{\"X\":{\"STONE\":\"kaputt\"},\"Y\":5}"));
    }

    @Test
    void statistik() throws Exception {
        MarketStats s = MarketStats.of(MarketParser.parsePrices(SMALL), 5);
        assertEquals(4, s.itemCount());
        assertEquals(3, s.itemsWithBuy());
        assertEquals(2, s.itemsWithSell());
        assertEquals(1, s.itemsWithoutOrders());
        assertEquals(437, s.totalBuyOrders());
        assertEquals(132, s.totalSellOrders());
        assertEquals("COAL", s.cheapest().material());
        assertEquals("BEACON", s.mostExpensive().material());
        assertEquals(678.7, s.medianBuyPrice(), 1e-9);
        assertEquals(List.of("COAL", "DIAMOND_BLOCK", "BEACON"),
                s.cheapestTop().stream().map(MarketEntry::material).toList());
        assertEquals("DIAMOND_BLOCK", s.mostOrdersTop().get(0).material());
        assertFalse(s.mostOrdersTop().stream().anyMatch(e -> e.material().equals("DRAGON_EGG")));
    }

    @Test
    void historieGewichtetNachMenge() throws Exception {
        String h = "{\"HOURLY\":["
                + "{\"avgPrice\":600,\"minPrice\":590,\"maxPrice\":610,\"items\":100,\"transactions\":2,\"timestamp\":\"2026-10-15T10:00:00\"},"
                + "{\"avgPrice\":700,\"minPrice\":650,\"maxPrice\":720,\"items\":300,\"transactions\":3,\"timestamp\":\"2026-10-15T11:00:00\"},"
                + "{\"avgPrice\":999,\"minPrice\":1,\"maxPrice\":9999,\"items\":5,\"transactions\":1,\"timestamp\":\"2026-10-10T11:00:00\"}],"
                + "\"DAILY\":[{\"avgPrice\":500,\"minPrice\":1,\"maxPrice\":1,\"items\":10,\"transactions\":1,\"timestamp\":\"2026-10-14T02:00:00\"}]}";
        Instant now = Instant.parse("2026-10-15T12:00:00Z"); // 14:00 Berliner Zeit
        MarketHistory m = MarketParser.parseHistory(h, now);
        assertEquals((600 * 100 + 700 * 300) / 400.0, m.avg24h(), 1e-9);
        assertEquals(590, m.min24h(), 1e-9);
        assertEquals(720, m.max24h(), 1e-9);
        assertEquals(400, m.items24h());
        assertEquals(5, m.trades24h());
        assertEquals(500, m.avg7d(), 1e-9);
        assertNotNull(MarketParser.parseHistory(res("market-history.json"), Instant.parse("2026-10-15T12:00:00Z")).avg24h());
    }

    @Test
    void serviceCachedUndBehaeltDatenBeiFehler() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String[] answer = {SMALL};
        MarketService svc = new MarketService(url -> {
            calls.incrementAndGet();
            if (answer[0] == null) throw new IOException("Markt-API nicht erreichbar");
            return answer[0];
        }, "https://x.invalid/prices", m -> "https://x.invalid/history/" + m, Duration.ofSeconds(60),
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC));
        svc.loadNow();
        assertEquals(4, svc.snapshot().entries().size());
        assertNull(svc.lastError());

        answer[0] = null;
        svc.loadNow();
        assertEquals(4, svc.snapshot().entries().size(), "alte Daten bleiben");
        assertEquals("Markt-API nicht erreichbar", svc.lastError());

        answer[0] = "{}";
        svc.loadNow();
        assertNotNull(svc.lastError(), "leere Antwort ist ein Fehler");
        assertEquals(4, svc.snapshot().entries().size());

        // Cache: frische Daten -> kein neuer Abruf
        answer[0] = SMALL;
        svc.loadNow();
        int before = calls.get();
        svc.refreshIfStale();
        Thread.sleep(100);
        assertEquals(before, calls.get(), "innerhalb des Intervalls keine Anfrage");
        svc.shutdown();
    }
}
