package dev.opsuchtprices.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser für die OP SUCHT Markt-API.
 *
 * /market/prices: { "Kategorie": { "MATERIAL": [ {"orderSide":"BUY","activeOrders":n,"price":x},
 *                                                {"orderSide":"SELL", ...} ] } }
 * /market/history/{material}: { "HOURLY": [...], "DAILY": [...], "WEEKLY": [...], "MONTHLY": [...] }
 *   Eintrag: avgPrice, minPrice, maxPrice, items, transactions, timestamp (ohne Zeitzone)
 */
public final class MarketParser {
    private MarketParser() {}

    /** Zeitzone der History-Zeitstempel (die API liefert sie ohne Zone). */
    public static final ZoneId HISTORY_ZONE = ZoneId.of("Europe/Berlin");

    public static List<MarketEntry> parsePrices(String body) throws IOException {
        JsonObject root = object(body);
        List<MarketEntry> out = new ArrayList<>();
        for (String category : root.keySet()) {
            JsonElement catEl = root.get(category);
            if (catEl == null || !catEl.isJsonObject()) continue;
            JsonObject cat = catEl.getAsJsonObject();
            for (String material : cat.keySet()) {
                JsonElement sides = cat.get(material);
                if (sides == null || !sides.isJsonArray() || material.isBlank()) continue;
                Double buy = null, sell = null;
                int buyOrders = 0, sellOrders = 0;
                for (JsonElement s : sides.getAsJsonArray()) {
                    if (!s.isJsonObject()) continue;
                    JsonObject so = s.getAsJsonObject();
                    String side = str(so, "orderSide");
                    int orders = (int) Math.max(0, num(so, "activeOrders", 0));
                    double p = num(so, "price", Double.NaN);
                    Double price = orders > 0 && Double.isFinite(p) && p > 0 ? p : null;
                    if ("BUY".equalsIgnoreCase(side)) {
                        buy = price;
                        buyOrders = orders;
                    } else if ("SELL".equalsIgnoreCase(side)) {
                        sell = price;
                        sellOrders = orders;
                    }
                }
                out.add(new MarketEntry(material.trim().toUpperCase(Locale.ROOT), category, buy, buyOrders, sell, sellOrders));
            }
        }
        return out;
    }

    public static MarketHistory parseHistory(String body, Instant now) throws IOException {
        JsonObject root = object(body);
        List<double[]> hourly = points(root, "HOURLY"); // [epochSec, avg, min, max, items, trades]
        List<double[]> daily = points(root, "DAILY");
        long from24 = now.minus(Duration.ofHours(24)).getEpochSecond();
        long from7d = now.minus(Duration.ofDays(7)).getEpochSecond();

        double wSum = 0, iSum = 0, min = Double.MAX_VALUE, max = 0;
        long trades = 0;
        List<Double> hourAvg = new ArrayList<>();
        for (double[] p : hourly) {
            if (p[0] < from24) continue;
            hourAvg.add(p[1]);
            if (p[4] <= 0) continue;
            wSum += p[1] * p[4];
            iSum += p[4];
            trades += (long) p[5];
            if (p[2] > 0) min = Math.min(min, p[2]);
            max = Math.max(max, p[3]);
        }
        double w7 = 0, i7 = 0;
        for (double[] p : daily) {
            if (p[0] < from7d || p[4] <= 0) continue;
            w7 += p[1] * p[4];
            i7 += p[4];
        }
        return new MarketHistory(now,
                iSum > 0 ? wSum / iSum : null,
                iSum > 0 && min != Double.MAX_VALUE ? min : 0,
                iSum > 0 ? max : 0,
                (long) iSum, trades,
                i7 > 0 ? w7 / i7 : null,
                List.copyOf(hourAvg));
    }

    private static List<double[]> points(JsonObject root, String interval) {
        List<double[]> out = new ArrayList<>();
        JsonElement arr = root.get(interval);
        if (arr == null || !arr.isJsonArray()) return out;
        for (JsonElement el : arr.getAsJsonArray()) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            Instant ts = localTime(str(o, "timestamp"));
            double avg = num(o, "avgPrice", Double.NaN);
            if (ts == null || !Double.isFinite(avg) || avg <= 0) continue;
            out.add(new double[]{ts.getEpochSecond(), avg, num(o, "minPrice", 0), num(o, "maxPrice", 0),
                    Math.max(0, num(o, "items", 0)), Math.max(0, num(o, "transactions", 0))});
        }
        out.sort((a, b) -> Double.compare(a[0], b[0]));
        return out;
    }

    static Instant localTime(String s) {
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s).atZone(HISTORY_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static JsonObject object(String body) throws IOException {
        JsonElement root;
        try {
            root = JsonParser.parseString(body == null ? "" : body);
        } catch (JsonParseException e) {
            throw new IOException("Ungültige Antwort der Markt-API (kein JSON)");
        }
        if (root == null || !root.isJsonObject()) throw new IOException("Unerwartetes Format der Markt-API");
        return root.getAsJsonObject();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static double num(JsonObject o, String k, double def) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return def;
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
