package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.market.MarketService;
import dev.opsuchtprices.market.MerchantRates;
import dev.opsuchtprices.overlay.SlotInfo;
import dev.opsuchtprices.util.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shard-Wert des Inventars nach den Händler-Kursen der API (/merchant/rates).
 * Kurse werden alle paar Minuten im Hintergrund geladen, der Inventarwert einmal pro Sekunde berechnet.
 */
public final class ShardValue {
    private ShardValue() {}

    /** Ein Posten im Inventar: Item, Anzahl, Kurs, Wert. */
    public record Line(String label, String target, int count, double rate, double value) {}

    private static MarketService.Fetcher fetcher;
    private static ModConfig config;
    private static volatile List<MerchantRates.Rate> rates;
    private static volatile String error;
    private static volatile long lastFetchMs;
    private static final AtomicBoolean loading = new AtomicBoolean();

    private static volatile Map<String, Double> totals = Map.of();
    private static volatile List<Line> lines = List.of();
    private static int tickCounter;

    public static void init(MarketService.Fetcher httpFetcher, ModConfig modConfig) {
        fetcher = httpFetcher;
        config = modConfig;
    }

    /** Jeden Client-Tick aufrufen. */
    public static void tick(Minecraft mc, boolean active) {
        if (config == null || !config.jobShowShards || !active) return;
        long now = System.currentTimeMillis();
        long every = Math.max(1, config.shardRefreshMinutes) * 60_000L;
        long retry = error == null ? every : 60_000L;
        if (now - lastFetchMs > (rates == null ? retry : every) && loading.compareAndSet(false, true)) {
            lastFetchMs = now;
            Thread t = new Thread(ShardValue::load, "opsuchtprices-shards");
            t.setDaemon(true);
            t.start();
        }
        if (++tickCounter % 20 == 0 && mc.player != null) compute(mc.player.getInventory());
    }

    private static void load() {
        try {
            List<MerchantRates.Rate> r = MerchantRates.parse(fetcher.get(config.merchantRatesUrl));
            rates = List.copyOf(r);
            error = null;
            ModLog.debug("Händler-Kurse geladen: {}", r.size());
        } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
            ModLog.warn("Händler-Kurse konnten nicht geladen werden: {}", error);
        } finally {
            loading.set(false);
        }
    }

    private static void compute(Inventory inv) {
        List<MerchantRates.Rate> r = rates;
        if (r == null) return;
        Map<MerchantRates.Rate, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            CustomModelData cmd = st.get(DataComponents.CUSTOM_MODEL_DATA);
            List<Float> floats = cmd == null ? List.of() : cmd.floats();
            MerchantRates.Rate rate = MerchantRates.find(r, id, floats, SlotInfo.strip(st.getHoverName().getString()));
            if (rate != null) counts.merge(rate, st.getCount(), Integer::sum);
        }
        Map<String, Double> sum = new LinkedHashMap<>();
        List<Line> out = new ArrayList<>();
        for (var e : counts.entrySet()) {
            double v = e.getKey().rate() * e.getValue();
            sum.merge(e.getKey().target(), v, Double::sum);
            out.add(new Line(e.getKey().label(), e.getKey().target(), e.getValue(), e.getKey().rate(), v));
        }
        out.sort((a, b) -> Double.compare(b.value(), a.value()));
        totals = Map.copyOf(sum);
        lines = List.copyOf(out);
    }

    /** Summe für "opshards" bzw. "redcoins"; 0 wenn nichts passt. */
    public static double total(String target) {
        return totals.getOrDefault(target, 0.0);
    }

    public static boolean ready() {
        return rates != null;
    }

    public static String error() {
        return error;
    }

    public static List<Line> lines() {
        return lines;
    }

    /** Nur für Tests: Kurse und Summen direkt setzen. */
    public static void setForTest(List<MerchantRates.Rate> r, Map<String, Double> t) {
        rates = r;
        totals = t;
    }
}
