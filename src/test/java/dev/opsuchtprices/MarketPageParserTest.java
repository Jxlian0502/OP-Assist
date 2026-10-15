package dev.opsuchtprices;

import dev.opsuchtprices.overlay.MarketPage;
import dev.opsuchtprices.overlay.MarketPageParser;
import dev.opsuchtprices.overlay.SlotInfo;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Tests mit einer echten Aufnahme des OP SUCHT Marktplatz-Menüs (15.10.2026). */
class MarketPageParserTest {

    record Menu(String title, List<SlotInfo> slots) {}

    static final Pattern HEAD = Pattern.compile("=== MENÜ \"(.*)\" \\| Typ");
    static final Pattern SLOT = Pattern.compile("^  \\[(\\d+)] (\\S+?)(?: x(\\d+))? \\| ?([^|]*?)(?: \\|.*)?$");

    static List<Menu> menus() throws Exception {
        String text;
        try (InputStream in = MarketPageParserTest.class.getResourceAsStream("/markt-aufnahme-menues.txt")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<Menu> out = new ArrayList<>();
        String title = null;
        List<SlotInfo> slots = null;
        List<String> lore = null;
        int slot = 0; String id = null, name = null; int count = 1;
        for (String line : text.split("\n")) {
            Matcher h = HEAD.matcher(line);
            if (h.find()) {
                if (slots != null) { if (id != null) slots.add(new SlotInfo(slot, id, count, name, lore)); out.add(new Menu(title, slots)); }
                title = h.group(1); slots = new ArrayList<>(); id = null;
                continue;
            }
            Matcher s = SLOT.matcher(line);
            if (s.find()) {
                if (id != null) slots.add(new SlotInfo(slot, id, count, name, lore));
                slot = Integer.parseInt(s.group(1)); id = s.group(2);
                count = s.group(3) == null ? 1 : Integer.parseInt(s.group(3));
                name = SlotInfo.strip(s.group(4)); lore = new ArrayList<>();
            } else if (line.startsWith("        ") && lore != null) {
                lore.add(SlotInfo.strip(line));
            }
        }
        if (slots != null) { if (id != null) slots.add(new SlotInfo(slot, id, count, name, lore)); out.add(new Menu(title, slots)); }
        return out;
    }

    @Test
    void erkenntSeitentypen() throws Exception {
        List<Menu> all = menus();
        assertEquals(43, all.size());
        int overview = 0, detail = 0, other = 0;
        for (Menu m : all) {
            MarketPage p = MarketPageParser.parse(m.title(), m.slots());
            if (p instanceof MarketPage.Overview) overview++;
            else if (p instanceof MarketPage.Detail) detail++;
            else other++;
        }
        assertTrue(overview >= 10, "Übersichten: " + overview);
        assertTrue(detail >= 2, "Detailseiten: " + detail);
        assertTrue(other >= 10, "Bestätigungs-/Eingabemenüs bleiben Original: " + other);
    }

    @Test
    void uebersicht() throws Exception {
        MarketPage.Overview o = (MarketPage.Overview) MarketPageParser.parse(menus().get(0).title(), menus().get(0).slots());
        assertEquals(List.of("Bergbau", "Holz", "Kampf", "Nahrung", "Sonstiges", "Besonderes"),
                o.categories().stream().map(MarketPage.Button::label).toList());
        assertEquals(0, o.categories().get(0).slot());
        assertEquals(45, o.categories().get(5).slot());
        assertEquals(24, o.tiles().size(), "4 Reihen mit je 6 Items");
        MarketPage.Tile dia = o.tiles().stream().filter(t -> t.name().equals("Diamantblock")).findFirst().orElseThrow();
        assertEquals(14, dia.slot());
        assertEquals("minecraft:diamond_block", dia.itemId());
        assertEquals(663.5, dia.buyPrice(), 1e-9);
        assertEquals(600.1, dia.sellPrice(), 1e-9);
        MarketPage.Tile neth = o.tiles().stream().filter(t -> t.name().equals("Netheritbarren")).findFirst().orElseThrow();
        assertEquals(9599.7, neth.buyPrice(), 1e-9);
        assertEquals(52, o.next().slot());
        assertEquals(50, o.search().slot());
        assertEquals(49, o.myOrders().slot());
        assertEquals(8, o.sellItems().slot());
        assertEquals(53, o.mode().slot());
    }

    @Test
    void fehlenderVerkaufspreis() throws Exception {
        for (Menu m : menus()) {
            if (MarketPageParser.parse(m.title(), m.slots()) instanceof MarketPage.Overview o) {
                for (MarketPage.Tile t : o.tiles()) {
                    if (t.name().equals("Bambus")) {
                        assertEquals(1.2, t.buyPrice(), 1e-9);
                        assertNull(t.sellPrice());
                        return;
                    }
                }
            }
        }
        fail("Bambus nicht gefunden");
    }

    @Test
    void detailseite() throws Exception {
        MarketPage.Detail before = null, after = null;
        for (Menu m : menus()) {
            if (MarketPageParser.parse(m.title(), m.slots()) instanceof MarketPage.Detail d) {
                if (before == null) before = d; else if (after == null) after = d;
            }
        }
        assertNotNull(before);
        assertEquals(13, before.itemSlot());
        assertEquals("minecraft:sand", before.itemId());
        assertEquals("Sand", before.name());
        assertEquals(10, before.buySlot());
        assertEquals(5.9, before.buyPrice(), 1e-9);
        assertTrue(before.canBuy());
        assertEquals(11, before.sellSlot());
        assertEquals(2.5, before.sellPrice(), 1e-9);
        assertFalse(before.canSell(), "keine Items im Inventar");
        assertNull(before.inInventory());
        assertEquals(10, before.buyOrders().size());
        assertEquals(new MarketPage.OrderLevel(2.5, 912613, 1), before.buyOrders().get(0));
        assertEquals(new MarketPage.OrderLevel(1.0, 67442, 4), before.buyOrders().get(7));
        assertEquals(new MarketPage.OrderLevel(6.0, 836580, 369), before.sellOffers().get(1));
        assertEquals(27, before.back().slot());
        assertEquals(31, before.myOrders().slot());
        assertEquals(15, before.createBuyOrder().slot());
        assertEquals(16, before.createSellOffer().slot());

        // nach dem Kauf: 1 Sand im Inventar, Verkaufen möglich
        assertTrue(after.canSell());
        assertEquals(1L, after.inInventory());
        assertEquals(2.5, after.inventoryValue(), 1e-9);
        assertEquals("5%", after.tax());
    }

    static MarketPage page(int i) throws Exception {
        Menu m = menus().get(i);
        return MarketPageParser.parse(m.title(), m.slots());
    }

    @Test
    void kaufauftragMenge() throws Exception {
        MarketPage.Amount a = (MarketPage.Amount) page(8);
        assertEquals("Kaufauftrag", a.orderType());
        assertEquals(List.of(11, 13, 15), a.choices().stream().map(MarketPage.AmountChoice::slot).toList());
        assertEquals("1x Sand", a.choices().get(0).label());
        assertEquals("Ein Item", a.choices().get(0).hint());
        assertEquals("1.216x Sand", a.choices().get(2).label());
        assertEquals("Ganzes Inventar", a.choices().get(2).hint());
        assertEquals(31, a.custom().slot());
        assertEquals(27, a.back().slot());
    }

    @Test
    void kaufauftragPreis() throws Exception {
        MarketPage.Price p = (MarketPage.Price) page(9);
        assertEquals("Kaufauftrag", p.orderType());
        assertEquals(3, p.choices().size());
        MarketPage.PriceChoice best = p.choices().get(0);
        assertEquals(11, best.slot());
        assertEquals("Bestes Angebot", best.label());
        assertEquals(1L, best.amount());
        assertEquals(2.5, best.price(), 1e-9);
        assertEquals(2.5, best.total(), 1e-9);
        assertEquals(2.6, p.choices().get(1).price(), 1e-9);
        MarketPage.PriceChoice spread = p.choices().get(2);
        assertEquals("5% der Spanne", spread.label());
        assertEquals(2.7, spread.price(), 1e-9);
        assertEquals(3, spread.extra().size());
        assertEquals(31, p.custom().slot());
        // grosse Menge
        MarketPage.Price big = (MarketPage.Price) page(13);
        assertEquals(1216L, big.choices().get(0).amount());
        assertEquals(3040.0, big.choices().get(0).total(), 1e-9);
    }

    @Test
    void kaufauftragBestaetigen() throws Exception {
        MarketPage.Confirm c = (MarketPage.Confirm) page(10);
        assertEquals(13, c.slot());
        assertEquals("Sand", c.name());
        assertEquals("Kaufauftrag", c.orderType());
        assertEquals("Klicke 3x, um zu Bestätigen", c.serverHint());
        assertTrue(c.details().contains("Gesamt: 2,5$"), c.details().toString());
        assertEquals(27, c.back().slot());
        assertEquals("Klicke noch 2x, um zu Bestätigen", ((MarketPage.Confirm) page(11)).serverHint());
        // Auftrag abbrechen ist ebenfalls eine Bestätigung
        MarketPage.Confirm cancel = (MarketPage.Confirm) page(23);
        assertEquals("Auftrag abbrechen", cancel.name());
        assertEquals(18, cancel.back().slot());
    }

    @Test
    void deineAuftraege() throws Exception {
        MarketPage.Orders o = (MarketPage.Orders) page(22);
        assertEquals(3, o.orders().size());
        MarketPage.Order big = o.orders().get(1);
        assertEquals(29, big.slot());
        assertEquals("Sand", big.name());
        assertEquals("Kaufauftrag", big.type());
        assertEquals(1216L, big.amount());
        assertEquals(0L, big.filled());
        assertEquals(1216L, big.filledOf());
        assertEquals(0.0, big.percent(), 1e-9);
        assertEquals(2.5, big.price(), 1e-9);
        assertEquals(3040.0, big.total(), 1e-9);
        assertEquals("Offen", big.status());
        assertEquals(2.6, o.orders().get(2).price(), 1e-9);
        assertEquals("Alles", o.filter());
        assertEquals(List.of("Alles", "Einsammelbar", "Aktiv"), o.filterOptions());
        assertEquals(53, o.filterButton().slot());
        assertEquals(45, o.back().slot());
        assertTrue(o.extra().isEmpty());
        assertFalse(big.collectible(), "Offener Auftrag im Filter 'Alles' ist nicht einsammelbar");
        MarketPage.Orders coll = (MarketPage.Orders) MarketPageParser.parse("OPSUCHT » Marktplatz", List.of(
                new SlotInfo(28, "minecraft:sand", 1, "Sand", List.of("Kaufauftrag", "Status: Erfüllt")),
                new SlotInfo(45, "minecraft:paper", 1, "Zurück", List.of()),
                new SlotInfo(53, "minecraft:hopper", 1, "Filter", List.of("• Alles", "» Einsammelbar", "• Aktiv"))));
        assertEquals("Einsammelbar", coll.filter());
        assertTrue(coll.orders().get(0).collectible());
        MarketPage.Orders hint = (MarketPage.Orders) MarketPageParser.parse("OPSUCHT » Marktplatz", List.of(
                new SlotInfo(28, "minecraft:sand", 1, "Sand", List.of("Kaufauftrag", "Status: Erfüllt", "Klicke zum Einsammeln")),
                new SlotInfo(45, "minecraft:paper", 1, "Zurück", List.of()),
                new SlotInfo(53, "minecraft:hopper", 1, "Filter", List.of("» Alles", "• Einsammelbar", "• Aktiv"))));
        assertTrue(hint.orders().get(0).collectible());
        MarketPage.Orders empty = (MarketPage.Orders) page(40);
        assertTrue(empty.orders().isEmpty());
    }

    @Test
    void auftragslisteBleibtOriginal() throws Exception {
        assertNull(page(29), "Items verkaufen");
        assertNull(page(16), "Amboss-Eingabe");
    }

    @Test
    void fremdeMenuesWerdenNichtErkannt() {
        assertNull(MarketPageParser.parse("OPSUCHT » Items verkaufen", List.of()));
        assertNull(MarketPageParser.parse("Kiste", List.of(new SlotInfo(0, "minecraft:stone", 1, "Suche", List.of()))));
        assertNull(MarketPageParser.parse("OPSUCHT » Marktplatz", List.of(
                new SlotInfo(13, "minecraft:sand", 1, "Sand", List.of("Klicke 3x, um zu Bestätigen")))));
    }
}
