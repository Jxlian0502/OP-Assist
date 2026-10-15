package dev.opsuchtprices.model;

import java.time.Instant;

/**
 * Die für die Verkaufserkennung nötigen Felder einer OP SUCHT Auktion.
 *
 * @param uid              stabile Auktions-ID
 * @param state            z. B. "ACTIVE" oder "ENDED"
 * @param currentBid       aktuelles Gebot (Gesamtpreis des Stapels), null wenn fehlend/ungültig
 * @param instantBuyPrice  Sofortkaufpreis (Gesamtpreis), null wenn keiner
 * @param hasHighestBidder true, wenn ein Höchstbietender gesetzt ist
 * @param bidCount         Anzahl der Gebote
 * @param endTime          Ende der Auktion, null wenn unbekannt
 */
public record Auction(
        String uid,
        AuctionItem item,
        String state,
        Double currentBid,
        Double instantBuyPrice,
        boolean hasHighestBidder,
        int bidCount,
        Instant endTime) {
}
