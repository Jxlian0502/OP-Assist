package dev.opsuchtprices;

import dev.opsuchtprices.market.MerchantRates;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MerchantRatesTest {

    static List<MerchantRates.Rate> rates() throws Exception {
        try (var in = MerchantRatesTest.class.getResourceAsStream("/merchant-rates.json")) {
            return MerchantRates.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void parstKurseAusDerApi() throws Exception {
        List<MerchantRates.Rate> r = rates();
        assertEquals(10, r.size());
        MerchantRates.Rate dia = r.get(0);
        assertEquals("minecraft:diamond_block", dia.itemId());
        assertEquals("opshards", dia.target());
        assertEquals(7.3, dia.rate(), 1e-9);
        assertNull(dia.modelData());
        MerchantRates.Rate holz = r.stream().filter(x -> "Holzbündel".equals(x.name())).findFirst().orElseThrow();
        assertEquals("minecraft:paper", holz.itemId());
        assertEquals(625f, holz.modelData());
        assertEquals(19.62, holz.rate(), 1e-9);
        assertEquals(3, r.stream().filter(x -> x.modelData() != null && x.name() != null).count());
        assertTrue(r.stream().anyMatch(x -> x.target().equals("redcoins")));
    }

    @Test
    void ordnetInventarItemsZu() throws Exception {
        List<MerchantRates.Rate> r = rates();
        assertEquals(7.3, MerchantRates.find(r, "minecraft:diamond_block", List.of(), "Diamantblock").rate(), 1e-9);
        assertEquals("Holzbündel", MerchantRates.find(r, "minecraft:paper", List.of(625f), "Holzbündel").name());
        assertEquals("Gräbergemisch", MerchantRates.find(r, "minecraft:paper", List.of(626f), "Gräbergemisch").name());
        assertNull(MerchantRates.find(r, "minecraft:paper", List.of(), "Papier"), "normales Papier bringt nichts");
        assertNull(MerchantRates.find(r, "minecraft:paper", List.of(999f), "Irgendwas"));
        assertNull(MerchantRates.find(r, "minecraft:diamond_block", List.of(12f), "Custom Diamantblock"));
    }
}
