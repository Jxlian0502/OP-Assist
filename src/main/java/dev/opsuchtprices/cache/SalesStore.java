package dev.opsuchtprices.cache;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.opsuchtprices.model.SaleRecord;
import dev.opsuchtprices.util.AtomicFiles;
import dev.opsuchtprices.util.ModLog;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Speichert lokal beobachtete, nachweislich abgeschlossene Verkäufe je Item.
 * Thread-sicher (alle Zugriffe synchronisiert). Wird atomar in sales.json gesichert.
 */
public final class SalesStore {

    /** Zusammenfassung für den Tooltip. */
    public record Summary(SaleRecord last, int count, double avgPerItem) {}

    private final Map<String, Deque<SaleRecord>> byKey = new HashMap<>();
    private final Set<String> knownUids = new HashSet<>();
    private final int maxPerItem;
    private boolean dirty;

    public SalesStore(int maxPerItem) {
        this.maxPerItem = Math.max(1, maxPerItem);
    }

    /**
     * Fügt einen Verkauf hinzu. Liefert false, wenn die Auktion (uid) bereits
     * erfasst wurde oder der Preis ungültig ist.
     */
    public synchronized boolean record(SaleRecord sale) {
        if (sale == null || sale.uid() == null || !(sale.totalPrice() > 0)
                || !Double.isFinite(sale.totalPrice()) || sale.soldAt() == null) {
            return false;
        }
        if (!knownUids.add(sale.uid())) {
            return false;
        }
        Deque<SaleRecord> dq = byKey.computeIfAbsent(sale.itemKey(), k -> new ArrayDeque<>());
        dq.addLast(sale);
        while (dq.size() > maxPerItem) {
            SaleRecord removed = dq.removeFirst();
            knownUids.remove(removed.uid());
        }
        dirty = true;
        return true;
    }

    /** Ersetzt alle Verkäufe auf einmal (z. B. nach dem Laden vom Preis-Server). Ältere zuerst einfügen. */
    public synchronized void replaceAll(List<SaleRecord> sales) {
        byKey.clear();
        knownUids.clear();
        List<SaleRecord> sorted = new ArrayList<>(sales);
        sorted.sort((a, b) -> a.soldAt().compareTo(b.soldAt()));
        for (SaleRecord r : sorted) record(r);
        dirty = false;
    }

    /** Letzter Verkauf (nach Zeitpunkt) und Durchschnitt der gespeicherten Verkäufe. */
    public synchronized Optional<Summary> summary(String itemKey) {
        Deque<SaleRecord> dq = byKey.get(itemKey);
        if (dq == null || dq.isEmpty()) return Optional.empty();
        SaleRecord last = null;
        double sum = 0;
        for (SaleRecord r : dq) {
            sum += r.pricePerItem();
            if (last == null || r.soldAt().isAfter(last.soldAt())) last = r;
        }
        return Optional.of(new Summary(last, dq.size(), sum / dq.size()));
    }

    /** Die letzten n Verkäufe eines Items, neueste zuerst. */
    public synchronized List<SaleRecord> recent(String itemKey, int n) {
        Deque<SaleRecord> dq = byKey.get(itemKey);
        if (dq == null) return List.of();
        List<SaleRecord> list = new ArrayList<>(dq);
        list.sort((a, b) -> b.soldAt().compareTo(a.soldAt()));
        return List.copyOf(list.subList(0, Math.min(n, list.size())));
    }

    /** Bild-URL des Items (vom neuesten Verkauf, der eine hat), sonst null. */
    public synchronized String iconFor(String itemKey) {
        Deque<SaleRecord> dq = byKey.get(itemKey);
        if (dq == null) return null;
        String icon = null;
        Instant best = null;
        for (SaleRecord r : dq) {
            if (r.icon() != null && (best == null || r.soldAt().isAfter(best))) {
                icon = r.icon();
                best = r.soldAt();
            }
        }
        return icon;
    }

    /** Schlüssel aller Items, für die noch keine Bild-URL bekannt ist. */
    public synchronized java.util.Set<String> keysWithoutIcon() {
        java.util.Set<String> out = new HashSet<>();
        for (String k : byKey.keySet()) {
            if (iconFor(k) == null) out.add(k);
        }
        return out;
    }

    /** Trägt nachträglich Bild-URLs ein (nur bei Verkäufen ohne Bild). Liefert die Anzahl ergänzter Items. */
    public synchronized int applyIcons(Map<String, String> iconsByKey) {
        int items = 0;
        for (Map.Entry<String, String> e : iconsByKey.entrySet()) {
            Deque<SaleRecord> dq = byKey.get(e.getKey());
            if (dq == null || e.getValue() == null || iconFor(e.getKey()) != null) continue;
            Deque<SaleRecord> updated = new ArrayDeque<>();
            for (SaleRecord r : dq) updated.addLast(r.icon() == null ? r.withIcon(e.getValue()) : r);
            byKey.put(e.getKey(), updated);
            items++;
        }
        if (items > 0) dirty = true;
        return items;
    }

    /** Übersicht eines Items für die Liste. */
    public record ItemOverview(String key, String label, Summary summary) {}

    /** Alle Items mit Verkäufen, neuester Verkauf zuerst. */
    public synchronized List<ItemOverview> overview() {
        List<ItemOverview> out = new ArrayList<>();
        for (String key : byKey.keySet()) {
            summary(key).ifPresent(s -> out.add(new ItemOverview(key, s.last().displayLabel(), s)));
        }
        out.sort((a, b) -> b.summary().last().soldAt().compareTo(a.summary().last().soldAt()));
        return out;
    }

    public synchronized int totalSales() {
        int n = 0;
        for (Deque<SaleRecord> d : byKey.values()) n += d.size();
        return n;
    }

    public synchronized int itemCount() {
        return byKey.size();
    }

    // ---------- Persistenz ----------

    public synchronized void saveIfDirty(Path file) {
        if (!dirty || file == null) return; // file == null: Daten kommen vom Preis-Server, lokal nichts speichern
        try {
            AtomicFiles.writeString(file, toJson());
            dirty = false;
        } catch (IOException e) {
            ModLog.warn("sales.json konnte nicht gespeichert werden: {}", e.toString());
        }
    }

    synchronized String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("note", "Lokal beobachtete Verkäufe aus dem OP SUCHT Auktions-Stream");
        JsonArray arr = new JsonArray();
        for (Deque<SaleRecord> dq : byKey.values()) {
            for (SaleRecord r : dq) {
                JsonObject o = new JsonObject();
                o.addProperty("uid", r.uid());
                o.addProperty("itemKey", r.itemKey());
                o.addProperty("totalPrice", r.totalPrice());
                o.addProperty("amount", r.amount());
                o.addProperty("soldAt", r.soldAt().toString());
                o.addProperty("type", r.type());
                if (r.label() != null) o.addProperty("label", r.label());
                if (r.icon() != null) o.addProperty("icon", r.icon());
                arr.add(o);
            }
        }
        root.add("sales", arr);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }

    public static SalesStore load(Path file, int maxPerItem) {
        SalesStore store = new SalesStore(maxPerItem);
        try {
            String json = AtomicFiles.readStringOrNull(file);
            if (json != null) {
                store.loadJson(json);
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            ModLog.warn("sales.json konnte nicht gelesen werden, starte leer: {}", e.toString());
            return new SalesStore(maxPerItem);
        }
        store.dirty = false;
        return store;
    }

    synchronized void loadJson(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return;
        JsonElement arr = root.getAsJsonObject().get("sales");
        if (arr == null || !arr.isJsonArray()) return;
        List<SaleRecord> all = new ArrayList<>();
        for (JsonElement el : arr.getAsJsonArray()) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            try {
                all.add(new SaleRecord(
                        o.get("uid").getAsString(),
                        o.get("itemKey").getAsString(),
                        o.get("totalPrice").getAsDouble(),
                        o.get("amount").getAsInt(),
                        Instant.parse(o.get("soldAt").getAsString()),
                        o.has("type") ? o.get("type").getAsString() : "UNKNOWN",
                        o.has("label") ? o.get("label").getAsString() : null,
                        o.has("icon") ? o.get("icon").getAsString() : null));
            } catch (DateTimeParseException | NullPointerException | UnsupportedOperationException
                     | NumberFormatException | IllegalStateException e) {
                // einzelner kaputter Eintrag: überspringen
            }
        }
        all.sort((a, b) -> a.soldAt().compareTo(b.soldAt()));
        for (SaleRecord r : all) record(r);
    }
}
