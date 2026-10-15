package dev.opsuchtprices.tooltip;

import dev.opsuchtprices.cache.SalesStore;
import dev.opsuchtprices.util.PriceFormat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Baut die zwei Tooltip-Zeilen: letzter AH-Verkauf und Durchschnittspreis.
 * Preise pro Stück (Verkaufspreis / Stapelgröße).
 */
public final class TooltipFormatter {
    private TooltipFormatter() {}

    public static List<TooltipLine> build(Optional<SalesStore.Summary> summary, String currency, Instant now) {
        if (summary.isEmpty()) {
            return List.of(TooltipLine.muted("Noch keine AH-Verkäufe erfasst"));
        }
        SalesStore.Summary s = summary.get();
        return List.of(
                TooltipLine.value("Letzter AH-Verkauf",
                        PriceFormat.price(s.last().pricePerItem(), currency) + " (" + PriceFormat.ago(s.last().soldAt(), now) + ")"),
                TooltipLine.value("Durchschnittspreis",
                        PriceFormat.price(s.avgPerItem(), currency) + " (" + s.count()
                                + (s.count() == 1 ? " Verkauf)" : " Verkäufe)")));
    }
}
