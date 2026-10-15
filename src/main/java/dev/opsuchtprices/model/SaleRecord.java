package dev.opsuchtprices.model;

import java.time.Instant;

/**
 * Ein nachweislich abgeschlossener Verkauf, beobachtet über den Event-Stream
 * (auction.sold oder auction.instant_bought).
 *
 * @param uid          Auktions-ID (zur Duplikaterkennung)
 * @param itemKey      Schlüssel des Items (siehe ItemKey#asString)
 * @param totalPrice   Gesamtpreis des Stapels
 * @param amount       Stapelgröße
 * @param soldAt       Zeitpunkt, an dem die Mod das Verkaufsereignis empfangen hat
 * @param type         "INSTANT_BUY" oder "AUCTION_SOLD"
 * @param label        lesbarer Item-Name für die Liste (kann null sein bei alten Einträgen)
 * @param icon         Bild-URL des Items aus der API (kann null sein)
 */
public record SaleRecord(
        String uid,
        String itemKey,
        double totalPrice,
        int amount,
        Instant soldAt,
        String type,
        String label,
        String icon) {

    public SaleRecord(String uid, String itemKey, double totalPrice, int amount, Instant soldAt, String type) {
        this(uid, itemKey, totalPrice, amount, soldAt, type, null, null);
    }

    public SaleRecord(String uid, String itemKey, double totalPrice, int amount, Instant soldAt, String type,
                      String label) {
        this(uid, itemKey, totalPrice, amount, soldAt, type, label, null);
    }

    public SaleRecord withIcon(String newIcon) {
        return new SaleRecord(uid, itemKey, totalPrice, amount, soldAt, type, label, newIcon);
    }

    /** Anzeigename; für alte Einträge ohne Label aus dem Schlüssel abgeleitet. */
    public String displayLabel() {
        if (label != null && !label.isBlank()) return label;
        String[] p = itemKey.split("\\|", -1);
        String material = p.length > 0 ? p[0] : itemKey;
        String name = p.length > 1 ? p[1] : "";
        String ench = p.length > 2 ? p[2] : "";
        String base = name.isEmpty() ? material : name + " (" + material + ")";
        return ench.isEmpty() ? base : base + " [" + ench.replace("minecraft:", "") + "]";
    }

    public double pricePerItem() {
        return totalPrice / Math.max(1, amount);
    }
}
