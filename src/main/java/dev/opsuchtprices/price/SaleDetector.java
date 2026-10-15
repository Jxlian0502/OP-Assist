package dev.opsuchtprices.price;

import dev.opsuchtprices.api.AuctionStreamEvent;
import dev.opsuchtprices.matching.ItemMatcher;
import dev.opsuchtprices.matching.ItemSignature;
import dev.opsuchtprices.model.Auction;
import dev.opsuchtprices.model.SaleRecord;

import java.time.Instant;

/**
 * Entscheidet, ob ein Stream-Ereignis einen tatsächlich abgeschlossenen Verkauf belegt.
 *
 * Streng:
 * - auction.instant_bought: Verkauf zum Sofortkaufpreis (instantBuyPrice muss vorhanden sein).
 * - auction.sold: Verkauf an den Höchstbietenden (highestBidder und currentBid müssen vorhanden sein).
 * - Alles andere (expired, cancelled, removed, verschwundene Angebote) ist KEIN Verkauf.
 */
public final class SaleDetector {
    private SaleDetector() {}

    public static SaleRecord detect(AuctionStreamEvent ev, Instant receivedAt) {
        if (ev == null || ev.auction() == null) return null;
        Auction a = ev.auction();
        Double price;
        String type;
        switch (ev.type()) {
            case INSTANT_BOUGHT -> {
                price = a.instantBuyPrice();
                type = "INSTANT_BUY";
            }
            case SOLD -> {
                price = a.hasHighestBidder() ? a.currentBid() : null;
                type = "AUCTION_SOLD";
            }
            default -> {
                return null;
            }
        }
        if (price == null || !(price > 0) || !Double.isFinite(price)) return null;
        String key = ItemMatcher.keyFor(ItemSignature.of(a.item())).asString();
        return new SaleRecord(a.uid(), key, price, Math.max(1, a.item().amount()), receivedAt, type, label(a),
                a.item().icon());
    }

    /** z. B. "Jxlians Modpicke (DIAMOND_PICKAXE)" oder "NETHERITE_SWORD [sharpness 5]". */
    static String label(Auction a) {
        String name = a.item().displayName();
        String material = a.item().material();
        String base = name == null || name.isBlank() ? material : name.trim() + " (" + material + ")";
        if (a.item().enchantments().isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base).append(" [");
        boolean first = true;
        for (var e : a.item().enchantments().entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey().replace("minecraft:", "")).append(' ').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
