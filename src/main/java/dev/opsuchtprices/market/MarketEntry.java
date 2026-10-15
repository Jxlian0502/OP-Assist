package dev.opsuchtprices.market;

/**
 * Ein Item im OP SUCHT Markt (Orderbuch), wie es /market/prices liefert.
 * Die API liefert pro Item genau zwei Seiten: BUY und SELL, jeweils mit
 * Preis pro Stück und Anzahl aktiver Orders. Verkäufer, Mengen oder einzelne
 * Angebote liefert die API NICHT.
 *
 * @param material   Bukkit-Materialname, z. B. "DIAMOND_BLOCK"
 * @param category   Marktkategorie, z. B. "Bergbau"
 * @param buyPrice   Preis der BUY-Seite pro Stück, null wenn keine Orders
 * @param buyOrders  aktive Orders der BUY-Seite
 * @param sellPrice  Preis der SELL-Seite pro Stück, null wenn keine Orders
 * @param sellOrders aktive Orders der SELL-Seite
 */
public record MarketEntry(
        String material,
        String category,
        Double buyPrice,
        int buyOrders,
        Double sellPrice,
        int sellOrders) {

    public int totalOrders() {
        return buyOrders + sellOrders;
    }

    /** Differenz BUY - SELL, null wenn eine Seite fehlt. */
    public Double spread() {
        return buyPrice == null || sellPrice == null ? null : buyPrice - sellPrice;
    }

    public boolean hasAnyOrders() {
        return buyPrice != null || sellPrice != null;
    }
}
