package dev.opsuchtprices.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Statistiken über den gesamten Markt, berechnet aus den geladenen Orderbuch-Daten.
 * Nicht berechenbar (die API liefert es nicht): Verkäufer, angebotene Mengen, Gesamtwert.
 */
public record MarketStats(
        int itemCount,
        int itemsWithBuy,
        int itemsWithSell,
        int itemsWithoutOrders,
        long totalBuyOrders,
        long totalSellOrders,
        MarketEntry cheapest,
        MarketEntry mostExpensive,
        Double medianBuyPrice,
        List<MarketEntry> cheapestTop,
        List<MarketEntry> expensiveTop,
        List<MarketEntry> mostOrdersTop) {

    public static MarketStats of(List<MarketEntry> entries, int topN) {
        int withBuy = 0, withSell = 0, none = 0;
        long buyOrders = 0, sellOrders = 0;
        List<MarketEntry> priced = new ArrayList<>();
        for (MarketEntry e : entries) {
            if (e.buyPrice() != null) {
                withBuy++;
                priced.add(e);
            }
            if (e.sellPrice() != null) withSell++;
            if (!e.hasAnyOrders()) none++;
            buyOrders += e.buyOrders();
            sellOrders += e.sellOrders();
        }
        priced.sort(Comparator.comparingDouble(MarketEntry::buyPrice));
        Double median = null;
        if (!priced.isEmpty()) {
            int n = priced.size();
            median = n % 2 == 1 ? priced.get(n / 2).buyPrice()
                    : (priced.get(n / 2 - 1).buyPrice() + priced.get(n / 2).buyPrice()) / 2;
        }
        List<MarketEntry> cheap = top(priced, topN, x -> true);
        List<MarketEntry> expensive = new ArrayList<>(priced);
        java.util.Collections.reverse(expensive);
        List<MarketEntry> byOrders = new ArrayList<>(entries);
        byOrders.sort(Comparator.comparingInt(MarketEntry::totalOrders).reversed()
                .thenComparing(MarketEntry::material));
        return new MarketStats(entries.size(), withBuy, withSell, none, buyOrders, sellOrders,
                priced.isEmpty() ? null : priced.get(0),
                priced.isEmpty() ? null : priced.get(priced.size() - 1),
                median, cheap, top(expensive, topN, x -> true),
                top(byOrders, topN, e -> e.totalOrders() > 0));
    }

    private static List<MarketEntry> top(List<MarketEntry> sorted, int n, Predicate<MarketEntry> keep) {
        List<MarketEntry> out = new ArrayList<>();
        for (MarketEntry e : sorted) {
            if (out.size() >= n) break;
            if (keep.test(e)) out.add(e);
        }
        return List.copyOf(out);
    }
}
