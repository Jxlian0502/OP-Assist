package dev.opsuchtprices.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.opsuchtprices.model.Auction;

/**
 * Ein dekodiertes Ereignis aus dem Auktions-Stream.
 * Ereignisnamen laut API-Beschreibung von https://api.opsucht.net/auctions.
 */
public record AuctionStreamEvent(Type type, Auction auction, String uid) {

    public enum Type {
        CREATED("auction.created"),
        BID_PLACED("auction.bid_placed"),
        INSTANT_BOUGHT("auction.instant_bought"),
        SOLD("auction.sold"),
        EXPIRED("auction.expired"),
        CANCELLED("auction.cancelled"),
        UPDATED("auction.updated"),
        REMOVED("auction.removed"),
        STREAM_RESET("stream.reset"),
        UNKNOWN("");

        public final String wireName;

        Type(String wireName) {
            this.wireName = wireName;
        }

        public static Type of(String name) {
            for (Type t : values()) {
                if (t.wireName.equals(name)) return t;
            }
            return UNKNOWN;
        }
    }

    /**
     * Dekodiert ein SSE-Ereignis. Die Ereignisse enthalten laut API "die vollständige
     * Auktion". Da kein Beispiel dokumentiert ist, werden sowohl das nackte
     * Auktionsobjekt als auch ein Wrapper {"auction": {...}} akzeptiert.
     * Liefert null, wenn die Daten nicht verwertbar sind.
     */
    public static AuctionStreamEvent decode(SseParser.Event ev) {
        Type type = Type.of(ev.event());
        if (type == Type.STREAM_RESET) {
            return new AuctionStreamEvent(type, null, null);
        }
        if (type == Type.UNKNOWN) {
            return null;
        }
        String data = ev.data() == null ? "" : ev.data().trim();
        if (type == Type.REMOVED) {
            String uid = extractUid(data);
            return uid == null ? null : new AuctionStreamEvent(type, null, uid);
        }
        JsonObject obj = parseObject(data);
        if (obj == null) return null;
        if (!obj.has("uid") && obj.has("auction") && obj.get("auction").isJsonObject()) {
            obj = obj.getAsJsonObject("auction");
        }
        Auction a = AuctionParser.parseAuction(obj);
        return a == null ? null : new AuctionStreamEvent(type, a, a.uid());
    }

    static String extractUid(String data) {
        if (data.isEmpty()) return null;
        JsonElement el;
        try {
            el = JsonParser.parseString(data);
        } catch (JsonParseException e) {
            return data.matches("[0-9a-fA-F-]{8,64}") ? data : null;
        }
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("uid") && o.get("uid").isJsonPrimitive()) return o.get("uid").getAsString();
        }
        return null;
    }

    private static JsonObject parseObject(String data) {
        try {
            JsonElement el = JsonParser.parseString(data);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }
}
