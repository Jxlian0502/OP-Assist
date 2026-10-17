package dev.opsuchtprices.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import dev.opsuchtprices.model.SaleRecord;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Datenformat der Preis-Schnittstelle (Server des AH-Sammlers ↔ Mod). Wird von beiden Seiten
 * verwendet, damit sie immer zusammenpassen.
 *
 * {"v":1,"generatedAt":<ms>,"sales":[{"u":uid,"k":itemKey,"p":totalPrice,"a":amount,
 *  "t":soldAt-ms,"y":type,"l":label,"i":icon}, ...]}
 */
public final class PriceApiJson {
    private PriceApiJson() {}

    public static final int VERSION = 1;

    public record Result(long generatedAt, List<SaleRecord> sales) {}

    public static String write(List<SaleRecord> sales, long generatedAt) {
        StringWriter sw = new StringWriter(Math.max(64, sales.size() * 120));
        try (JsonWriter w = new JsonWriter(sw)) {
            w.beginObject();
            w.name("v").value(VERSION);
            w.name("generatedAt").value(generatedAt);
            w.name("sales").beginArray();
            for (SaleRecord r : sales) {
                w.beginObject();
                w.name("u").value(r.uid());
                w.name("k").value(r.itemKey());
                w.name("p").value(r.totalPrice());
                w.name("a").value(r.amount());
                w.name("t").value(r.soldAt().toEpochMilli());
                w.name("y").value(r.type());
                if (r.label() != null) w.name("l").value(r.label());
                if (r.icon() != null) w.name("i").value(r.icon());
                w.endObject();
            }
            w.endArray();
            w.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sw.toString();
    }

    /** Liest eine Antwort der Schnittstelle. Unvollständige Einträge werden übersprungen. */
    public static Result parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Keine gültige Antwort");
        JsonObject o = root.getAsJsonObject();
        long generatedAt = o.has("generatedAt") ? o.get("generatedAt").getAsLong() : System.currentTimeMillis();
        List<SaleRecord> out = new ArrayList<>();
        JsonArray arr = o.has("sales") && o.get("sales").isJsonArray() ? o.getAsJsonArray("sales") : new JsonArray();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject s = el.getAsJsonObject();
            try {
                double price = s.get("p").getAsDouble();
                if (!(price > 0) || !Double.isFinite(price)) continue;
                out.add(new SaleRecord(
                        s.get("u").getAsString(),
                        s.get("k").getAsString(),
                        price,
                        Math.max(1, s.get("a").getAsInt()),
                        Instant.ofEpochMilli(s.get("t").getAsLong()),
                        s.has("y") ? s.get("y").getAsString() : "UNKNOWN",
                        s.has("l") && !s.get("l").isJsonNull() ? s.get("l").getAsString() : null,
                        s.has("i") && !s.get("i").isJsonNull() ? s.get("i").getAsString() : null));
            } catch (RuntimeException e) {
                // einzelner unvollständiger Eintrag: überspringen
            }
        }
        return new Result(generatedAt, out);
    }
}
