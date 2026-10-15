package dev.opsuchtprices.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wechselkurse des Händlers ({@code /merchant/rates}): welches Item wie viele Shards (oder Redcoins) bringt.
 * Beispiel: {"source":"diamond_block","target":"opshards","base":7.0,"exchangeRate":7.3}.
 * Custom Items kommen als Item-String mit Komponenten, z. B.
 * {@code minecraft:paper[custom_name={... text: "Holzbündel" ...},custom_model_data={floats: [625.0f]},...]}.
 */
public final class MerchantRates {
    private MerchantRates() {}

    /** Ein Kurs. modelData/name sind null bei normalen Items. rate = Ziel-Währung pro Item. */
    public record Rate(String itemId, Float modelData, String name, String target, double base, double rate) {

        /** Passt ein Item (ID, Custom-Model-Data-Floats, angezeigter Name ohne Farbcodes) zu diesem Kurs? */
        public boolean matches(String id, List<Float> modelFloats, String displayName) {
            if (!itemId.equals(id)) return false;
            boolean hasModel = modelFloats != null && !modelFloats.isEmpty();
            if (modelData != null) {
                if (!hasModel) return false;
                for (Float f : modelFloats) if (f != null && Math.abs(f - modelData) < 0.001f) return true;
                return false;
            }
            if (name != null) return name.equalsIgnoreCase(displayName == null ? "" : displayName.trim());
            return !hasModel; // normales Item: keine Custom-Variante
        }

        /** Anzeigename für Listen. */
        public String label() {
            if (name != null) return name;
            String p = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
            return p.replace('_', ' ');
        }
    }

    private static final Pattern MODEL = Pattern.compile("custom_model_data=\\{floats:\\s*\\[\\s*(-?[\\d.]+)f?");
    private static final Pattern NAME = Pattern.compile("text:\\s*\"([^\"]+)\"");

    public static List<Rate> parse(String json) {
        List<Rate> out = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (!o.has("source") || !o.has("exchangeRate")) continue;
            Rate r = parseSource(o.get("source").getAsString(),
                    o.has("target") ? o.get("target").getAsString() : "opshards",
                    o.has("base") ? o.get("base").getAsDouble() : 0,
                    o.get("exchangeRate").getAsDouble());
            if (r != null) out.add(r);
        }
        return out;
    }

    static Rate parseSource(String source, String target, double base, double rate) {
        if (source == null || source.isBlank()) return null;
        String s = source.trim();
        int br = s.indexOf('[');
        String id = (br >= 0 ? s.substring(0, br) : s).trim().toLowerCase(Locale.ROOT);
        if (!id.contains(":")) id = "minecraft:" + id;
        Float model = null;
        String name = null;
        if (br >= 0) {
            String comps = s.substring(br);
            Matcher m = MODEL.matcher(comps);
            if (m.find()) model = Float.parseFloat(m.group(1));
            int cn = comps.indexOf("custom_name=");
            if (cn < 0) cn = comps.indexOf("item_name=");
            if (cn >= 0) {
                Matcher n = NAME.matcher(comps.substring(cn));
                if (n.find()) name = n.group(1);
            }
        }
        return new Rate(id, model, name, target.toLowerCase(Locale.ROOT), base, rate);
    }

    /** Erster passender Kurs oder null. */
    public static Rate find(List<Rate> rates, String id, List<Float> modelFloats, String displayName) {
        if (rates == null) return null;
        for (Rate r : rates) if (r.matches(id, modelFloats, displayName)) return r;
        return null;
    }
}
