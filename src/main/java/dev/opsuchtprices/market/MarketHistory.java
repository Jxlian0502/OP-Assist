package dev.opsuchtprices.market;

import java.time.Instant;
import java.util.List;

/**
 * Zusammenfassung der Markthistorie eines Items (aus /market/history/{material}).
 *
 * @param fetchedAt     Abrufzeitpunkt
 * @param avg24h        mengengewichteter Durchschnitt der letzten 24 Stunden, null wenn keine Transaktionen
 * @param min24h        niedrigster Preis der letzten 24 Stunden
 * @param max24h        höchster Preis der letzten 24 Stunden
 * @param items24h      gehandelte Items der letzten 24 Stunden
 * @param trades24h     Transaktionen der letzten 24 Stunden
 * @param avg7d         mengengewichteter Durchschnitt der letzten 7 Tage (DAILY), null wenn keine Daten
 * @param hourlyAvg     Stundendurchschnitte der letzten 24 Stunden (für eine Mini-Grafik)
 */
public record MarketHistory(
        Instant fetchedAt,
        Double avg24h,
        double min24h,
        double max24h,
        long items24h,
        long trades24h,
        Double avg7d,
        List<Double> hourlyAvg) {
}
