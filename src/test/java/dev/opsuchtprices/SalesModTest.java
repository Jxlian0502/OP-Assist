package dev.opsuchtprices;

import dev.opsuchtprices.api.AuctionStreamEvent;
import dev.opsuchtprices.api.SseParser;
import dev.opsuchtprices.cache.SaleTracker;
import dev.opsuchtprices.cache.SalesStore;
import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.matching.ItemMatcher;
import dev.opsuchtprices.matching.ItemSignature;
import dev.opsuchtprices.model.SaleRecord;
import dev.opsuchtprices.tooltip.TooltipFormatter;
import dev.opsuchtprices.tooltip.TooltipLine;
import dev.opsuchtprices.util.PriceFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Tests mit Mock-Daten, ohne Netzwerk. */
class SalesModTest {

    static final Instant NOW = Instant.parse("2026-10-14T20:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Auktion im Format der OP SUCHT API. */
    static String auction(String uid, String material, String name, int amount, String extra) {
        return "{\"uid\":\"" + uid + "\",\"seller\":\"00000000-0000-0000-0000-000000000001\","
                + "\"item\":{\"material\":\"" + material + "\",\"amount\":" + amount
                + (name == null ? "" : ",\"displayName\":\"" + name + "\"")
                + ",\"lore\":[],\"enchantments\":{}},\"category\":\"custom_items\",\"state\":\"ACTIVE\""
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    static AuctionStreamEvent event(String name, String data) {
        return AuctionStreamEvent.decode(new SseParser.Event("1", name, data));
    }

    static String key(String material, String name) {
        return ItemMatcher.keyFor(new ItemSignature(material, name, new TreeMap<>())).asString();
    }

    static String text(List<TooltipLine> l) {
        StringBuilder sb = new StringBuilder();
        for (TooltipLine x : l) sb.append(x.plain()).append('\n');
        return sb.toString();
    }

    private SaleTracker tracker(Path dir) {
        return new SaleTracker(new SalesStore(50), dir.resolve("sales.json"), CLOCK, () -> true, null);
    }

    // ---------- Verkaufserkennung ----------

    @Test
    void sofortkaufWirdAlsVerkaufGespeichert() throws Exception {
        SaleTracker t = tracker(Files.createTempDirectory("t"));
        t.onStreamEvent(event("auction.instant_bought",
                auction("u1", "PAPER", "Fishing Mastery Boosterpack", 64, "\"instantBuyPrice\":8000000,\"currentBid\":7000000.0")));
        SalesStore.Summary s = t.sales().summary(key("PAPER", "Fishing Mastery Boosterpack")).orElseThrow();
        assertEquals(125_000.0, s.last().pricePerItem(), 1e-9);
        assertEquals(NOW, s.last().soldAt());
    }

    @Test
    void versteigerungMitHoechstbietendemIstVerkauf() throws Exception {
        SaleTracker t = tracker(Files.createTempDirectory("t"));
        t.onStreamEvent(event("auction.sold", auction("u2", "GOLDEN_HORSE_ARMOR", "Oxidtalisman", 1,
                "\"currentBid\":160.0,\"highestBidder\":\"00000000-0000-0000-0000-000000000009\"")));
        assertEquals(160.0, t.sales().summary(key("GOLDEN_HORSE_ARMOR", "Oxidtalisman")).orElseThrow().last().totalPrice(), 1e-9);
    }

    @Test
    void ablaufAbbruchUndUpdatesSindKeinVerkauf() throws Exception {
        SaleTracker t = tracker(Files.createTempDirectory("t"));
        String a = auction("u3", "DIRT", null, 1, "\"instantBuyPrice\":10,\"currentBid\":5.0");
        for (String ev : List.of("auction.expired", "auction.cancelled", "auction.updated", "auction.created", "auction.bid_placed")) {
            t.onStreamEvent(event(ev, a));
        }
        t.onStreamEvent(event("auction.removed", "{\"uid\":\"u3\"}"));
        // "sold" ohne Höchstbietenden ist nicht belegt
        t.onStreamEvent(event("auction.sold", a));
        assertEquals(0, t.sales().totalSales());
    }

    @Test
    void doppelteUndUngueltigeVerkaeufeWerdenIgnoriert() {
        SalesStore store = new SalesStore(50);
        assertTrue(store.record(new SaleRecord("u1", "k", 100, 1, NOW, "INSTANT_BUY")));
        assertFalse(store.record(new SaleRecord("u1", "k", 100, 1, NOW, "AUCTION_SOLD")));
        assertFalse(store.record(new SaleRecord("u2", "k", -5, 1, NOW, "INSTANT_BUY")));
        assertFalse(store.record(new SaleRecord("u3", "k", Double.NaN, 1, NOW, "INSTANT_BUY")));
        assertEquals(1, store.totalSales());
    }

    // ---------- Durchschnitt und letzter Verkauf ----------

    @Test
    void letzterVerkaufUndDurchschnitt() {
        SalesStore store = new SalesStore(50);
        store.record(new SaleRecord("a", "k", 100, 1, NOW.minusSeconds(600), "INSTANT_BUY"));
        store.record(new SaleRecord("c", "k", 6400, 32, NOW.minusSeconds(60), "INSTANT_BUY")); // 200 /Stk.
        store.record(new SaleRecord("b", "k", 300, 1, NOW.minusSeconds(300), "INSTANT_BUY"));
        SalesStore.Summary s = store.summary("k").orElseThrow();
        assertEquals("c", s.last().uid(), "jüngster Zeitpunkt, nicht Reihenfolge");
        assertEquals(3, s.count());
        assertEquals(200.0, s.avgPerItem(), 1e-9);
    }

    @Test
    void nurDieLetztenNVerkaeufeZaehlen() {
        SalesStore store = new SalesStore(2);
        for (int i = 0; i < 5; i++) store.record(new SaleRecord("u" + i, "k", 100 + i, 1, NOW.plusSeconds(i), "INSTANT_BUY"));
        assertEquals(2, store.totalSales());
        assertEquals(103.5, store.summary("k").orElseThrow().avgPerItem(), 1e-9);
    }

    @Test
    void uebersichtMitLesbarenNamenNeuesteZuerst() throws Exception {
        Path dir = Files.createTempDirectory("t");
        SaleTracker t = tracker(dir);
        t.onStreamEvent(event("auction.instant_bought",
                auction("u1", "DIAMOND_PICKAXE", "Jxlians Modpicke", 1, "\"instantBuyPrice\":2000000")));
        SalesStore store = t.sales();
        store.record(new SaleRecord("old", key("DIAMOND_BLOCK", null), 640, 64, NOW.minusSeconds(3600), "INSTANT_BUY"));
        List<SalesStore.ItemOverview> o = store.overview();
        assertEquals(2, o.size());
        assertEquals("Jxlians Modpicke (DIAMOND_PICKAXE)", o.get(0).label());
        assertEquals("DIAMOND_BLOCK", o.get(1).label(), "alte Einträge ohne Label: aus Schlüssel");
        t.tick();
        assertEquals("Jxlians Modpicke (DIAMOND_PICKAXE)",
                SalesStore.load(dir.resolve("sales.json"), 50).overview().get(0).label(), "Label wird gespeichert");
    }

    @Test
    void bildUrlWirdGespeichertUndNachgeladen() throws Exception {
        Path dir = Files.createTempDirectory("t");
        SaleTracker t = tracker(dir);
        t.onStreamEvent(event("auction.instant_bought", "{\"uid\":\"w1\",\"item\":{\"material\":\"PAPER\",\"amount\":1,"
                + "\"displayName\":\"Angler Wilko\",\"icon\":\"https://items.opsucht.net/oIotRt-AIIa_.png\"},"
                + "\"state\":\"ACTIVE\",\"instantBuyPrice\":2500000}"));
        String wilko = key("PAPER", "Angler Wilko");
        assertEquals("https://items.opsucht.net/oIotRt-AIIa_.png", t.sales().iconFor(wilko));

        // alter Eintrag ohne Bild wird aus den aktiven Auktionen ergänzt
        String pick = key("GOLDEN_HORSE_ARMOR", "Oxidtalisman");
        t.sales().record(new SaleRecord("old", pick, 160, 1, NOW.minusSeconds(60), "AUCTION_SOLD"));
        assertNull(t.sales().iconFor(pick));
        String active = "[" + "{\"uid\":\"a1\",\"item\":{\"material\":\"GOLDEN_HORSE_ARMOR\",\"amount\":1,"
                + "\"displayName\":\"Oxidtalisman\",\"icon\":\"https://items.opsucht.net/DG9JA8cVFcBn.png\"},\"state\":\"ACTIVE\"},"
                + "{\"uid\":\"a2\",\"item\":{\"material\":\"DIRT\",\"amount\":1,\"icon\":\"https://img.mc-api.io/dirt.png\"},\"state\":\"ACTIVE\"}]";
        assertEquals(1, t.backfillIcons(active));
        assertEquals("https://items.opsucht.net/DG9JA8cVFcBn.png", t.sales().iconFor(pick));
        assertTrue(t.sales().keysWithoutIcon().isEmpty());

        // Bild-URL übersteht Neustart
        t.tick();
        assertEquals("https://items.opsucht.net/oIotRt-AIIa_.png",
                SalesStore.load(dir.resolve("sales.json"), 50).iconFor(wilko));
    }

    // ---------- Speicherung ----------

    @Test
    void verkaeufeUeberlebenNeustart() throws Exception {
        Path dir = Files.createTempDirectory("t");
        SaleTracker t = tracker(dir);
        t.onStreamEvent(event("auction.instant_bought", auction("u1", "DIAMOND_BLOCK", null, 64, "\"instantBuyPrice\":54000")));
        t.tick(); // speichert
        assertTrue(Files.exists(dir.resolve("sales.json")));
        try (var files = Files.list(dir)) {
            assertEquals(1L, files.count(), "keine temporären Dateien übrig");
        }
        SalesStore loaded = SalesStore.load(dir.resolve("sales.json"), 50);
        assertEquals(843.75, loaded.summary(key("DIAMOND_BLOCK", null)).orElseThrow().avgPerItem(), 1e-9);
        assertFalse(loaded.record(new SaleRecord("u1", "x", 1, 1, NOW, "INSTANT_BUY")), "Duplikat nach Neustart erkannt");
    }

    @Test
    void kaputteDateiFuehrtNichtZumAbsturz() throws Exception {
        Path f = Files.createTempDirectory("t").resolve("sales.json");
        Files.writeString(f, "{ kaputt");
        assertEquals(0, SalesStore.load(f, 50).totalSales());
    }

    @Test
    void configStandardwerte() throws Exception {
        Path f = Files.createTempDirectory("t").resolve("config.json");
        ModConfig c = ModConfig.load(f);
        assertTrue(c.enabled);
        assertEquals("$", c.currencyLabel);
        assertTrue(Files.exists(f));
        Files.writeString(f, "{\"currencyLabel\": \"Coins\"}");
        assertEquals("$", ModConfig.load(f).currencyLabel, "alte Config wird umgestellt");
    }

    // ---------- Zuordnung ----------

    @Test
    void itemZuordnung() {
        assertEquals(key("PAPER", "Eiswerfer "), key("paper", "§bEiswerfer"));
        assertNotEquals(key("DIAMOND_SWORD", null), key("DIAMOND_SWORD", "Mein Schwert"));
        String s5 = ItemMatcher.keyFor(new ItemSignature("DIAMOND_SWORD", null, new TreeMap<>(Map.of("minecraft:sharpness", 5)))).asString();
        String s4 = ItemMatcher.keyFor(new ItemSignature("DIAMOND_SWORD", null, new TreeMap<>(Map.of("minecraft:sharpness", 4)))).asString();
        String s5b = ItemMatcher.keyFor(new ItemSignature("DIAMOND_SWORD", null, new TreeMap<>(Map.of("sharpness", 5)))).asString();
        assertNotEquals(s5, s4);
        assertEquals(s5, s5b);
        assertEquals("NETHERITE_AXE", ItemMatcher.materialFromItemId("minecraft:netherite_axe"));
    }

    // ---------- Stream ----------

    @Test
    void sseParser() {
        List<SseParser.Event> events = new ArrayList<>();
        SseParser p = new SseParser(events::add);
        for (String l : List.of(": ping", "id: 7", "event: auction.sold", "data: {\"a\":", "data: 1}", "")) p.accept(l);
        assertEquals(1, events.size());
        assertEquals("auction.sold", events.get(0).event());
        assertEquals("{\"a\":\n1}", events.get(0).data());
        assertEquals("7", p.lastEventId());
    }

    @Test
    void streamDatenMitWrapperUndKaputteDaten() {
        AuctionStreamEvent w = event("auction.instant_bought", "{\"auction\":" + auction("u9", "DIRT", null, 1, "\"instantBuyPrice\":5") + "}");
        assertEquals("u9", w.uid());
        assertNull(event("auction.sold", "kaputt"));
        assertNull(event("unbekannt", "{}"));
    }

    // ---------- Tooltip ----------

    @Test
    void tooltip() {
        SalesStore store = new SalesStore(50);
        store.record(new SaleRecord("a", "k", 110_000, 1, NOW.minusSeconds(3600), "INSTANT_BUY"));
        store.record(new SaleRecord("b", "k", 130_000, 1, NOW.minusSeconds(8 * 60), "INSTANT_BUY"));
        String tip = text(TooltipFormatter.build(store.summary("k"), "$", NOW));
        assertTrue(tip.contains("Letzter AH-Verkauf: 130.000$ (vor 8 Minuten)"), tip);
        assertTrue(tip.contains("Durchschnittspreis: 120.000$ (2 Verkäufe)"), tip);
        assertEquals("Noch keine AH-Verkäufe erfasst\n", text(TooltipFormatter.build(Optional.empty(), "$", NOW)));
        assertEquals("9,38", PriceFormat.price(9.375, ""));
    }
}
