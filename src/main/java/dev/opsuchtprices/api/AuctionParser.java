package dev.opsuchtprices.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.opsuchtprices.model.Auction;
import dev.opsuchtprices.model.AuctionItem;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Parst ein Auktionsobjekt im Format der OP SUCHT API
 * (Felder wie in https://api.opsucht.net/auctions/active).
 * Tolerant: fehlende oder ungültige Werte werden null, nichts wird geworfen.
 */
public final class AuctionParser {
    private AuctionParser() {}

    /** Liefert null, wenn Pflichtfelder (uid, item.material) fehlen. */
    public static Auction parseAuction(JsonObject o) {
        String uid = str(o, "uid");
        if (uid == null || uid.isBlank()) return null;
        JsonObject itemObj = obj(o, "item");
        if (itemObj == null) return null;
        AuctionItem item = parseItem(itemObj);
        if (item == null) return null;
        JsonObject bids = obj(o, "bids");
        return new Auction(
                uid.trim(),
                item,
                str(o, "state"),
                price(o, "currentBid"),
                price(o, "instantBuyPrice"),
                str(o, "highestBidder") != null,
                bids == null ? 0 : bids.size(),
                time(o, "endTime"));
    }

    /** Parst die Antwort von /auctions/active (JSON-Array); kaputte Einträge werden übersprungen. */
    public static java.util.List<Auction> parseList(String body) {
        java.util.List<Auction> out = new java.util.ArrayList<>();
        JsonElement root;
        try {
            root = com.google.gson.JsonParser.parseString(body == null ? "" : body);
        } catch (com.google.gson.JsonParseException e) {
            return out;
        }
        if (root == null || !root.isJsonArray()) return out;
        for (JsonElement el : root.getAsJsonArray()) {
            if (!el.isJsonObject()) continue;
            Auction a = parseAuction(el.getAsJsonObject());
            if (a != null) out.add(a);
        }
        return out;
    }

    static AuctionItem parseItem(JsonObject o) {
        String material = str(o, "material");
        if (material == null || material.isBlank()) return null;
        int amount = 1;
        JsonElement amt = o.get("amount");
        if (amt != null && amt.isJsonPrimitive() && amt.getAsJsonPrimitive().isNumber()) {
            amount = Math.max(1, amt.getAsInt());
        }
        SortedMap<String, Integer> ench = new TreeMap<>();
        JsonObject enchObj = obj(o, "enchantments");
        if (enchObj != null) {
            for (String k : enchObj.keySet()) {
                JsonElement v = enchObj.get(k);
                if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    ench.put(k.toLowerCase(Locale.ROOT), v.getAsInt());
                }
            }
        }
        return new AuctionItem(
                material.trim().toUpperCase(Locale.ROOT),
                str(o, "displayName"),
                amount,
                Collections.unmodifiableSortedMap(ench),
                str(o, "icon"));
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
        return e.getAsString();
    }

    static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Gültiger Preis: endliche Zahl > 0, sonst null. */
    static Double price(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        try {
            double v = e.getAsDouble();
            return Double.isFinite(v) && v > 0 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Instant time(JsonObject o, String key) {
        String s = str(o, key);
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
