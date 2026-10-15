package dev.opsuchtprices.overlay;

import java.util.List;

/** Erkannte Seite des OP SUCHT Marktplatz-Menüs. */
public sealed interface MarketPage {

    /** Ein Preisniveau aus "Top 10 Aufträge/Angebote": Preis pro Item, Menge, Anzahl Aufträge. */
    record OrderLevel(double price, long amount, int orders) {}

    record Button(int slot, String label) {}

    /** Ein Item auf der Übersichtsseite. Preise wie vom Server angezeigt, null = keiner. */
    record Tile(int slot, String itemId, String name, Double buyPrice, Double sellPrice) {}

    /** Übersicht mit Kategorien und Items. */
    record Overview(List<Button> categories, List<Tile> tiles, Button next, Button previous,
                    Button search, Button myOrders, Button sellItems, Button mode) implements MarketPage {}

    /** Auswahl in Schritt 1 (Menge): z. B. "1x Sand" / "Ein Item". */
    record AmountChoice(int slot, String label, String hint) {}

    /** Mengenauswahl (Kaufauftrag, Verkaufsangebot oder Sofortkauf mit Anzahl). orderType kann null sein. */
    record Amount(String orderType, List<AmountChoice> choices, Button custom, Button back) implements MarketPage {}

    /** Auswahl in Schritt 2 (Preis). Werte wie vom Server angezeigt, null = nicht angegeben. */
    record PriceChoice(int slot, String label, Long amount, Double price, Double total, List<String> extra) {}

    /** Preisauswahl für einen Auftrag/ein Angebot. */
    record Price(String orderType, List<PriceChoice> choices, Button custom, Button back) implements MarketPage {}

    /**
     * Bestätigungsseite ("Klicke 3x, um zu Bestätigen"). Jeder Klick im Overlay ist genau ein Klick
     * auf den Bestätigen-Slot des Servers; der Server zählt selbst mit.
     */
    record Confirm(int slot, String itemId, String name, String orderType, List<String> details,
                   String serverHint, Button back) implements MarketPage {}

    /** Detailseite eines Items mit Sofort-Kauf/-Verkauf. */
    record Detail(int itemSlot, String itemId, String name,
                  int buySlot, Double buyPrice, boolean canBuy,
                  int sellSlot, Double sellPrice, boolean canSell, Long inInventory, Double inventoryValue, String tax,
                  Button createBuyOrder, List<OrderLevel> buyOrders,
                  Button createSellOffer, List<OrderLevel> sellOffers,
                  Button back, Button myOrders,
                  /** false, wenn der Server "Keine Items im Inventar." meldet (Verkaufsangebot nicht möglich). */
                  boolean canSellOffer) implements MarketPage {}

    /** Ein eigener Auftrag/ein eigenes Angebot aus "Deine Aufträge". Werte wie vom Server angezeigt, null = fehlt. */
    record Order(int slot, String itemId, String name, String type, Long amount, Long filled, Long filledOf,
                 Double percent, Double price, Double total, String status, List<String> other,
                 /** true, wenn der Server den Auftrag als einsammelbar zeigt (Filter "Einsammelbar" oder Hinweis im Text). */
                 boolean collectible) {}

    /** "Deine Aufträge": Liste, Filter (ein Klick = nächster Filter), Zurück, sonstige Knöpfe (z. B. Seiten). */
    record Orders(List<Order> orders, String filter, List<String> filterOptions, Button filterButton,
                  Button back, List<Button> extra) implements MarketPage {}
}
