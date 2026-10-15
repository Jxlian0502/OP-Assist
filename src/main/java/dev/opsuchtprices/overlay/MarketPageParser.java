package dev.opsuchtprices.overlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt anhand des Menü-Inhalts, welche Marktplatz-Seite offen ist.
 * Erkennung über die Namen der Knöpfe (nicht nur Slot-Nummern), damit kleine
 * Layout-Änderungen des Servers nicht zu falschen Klicks führen.
 * Unbekannte Seiten liefern null: dann bleibt das Original-Menü sichtbar.
 */
public final class MarketPageParser {
    private MarketPageParser() {}

    private static final Pattern NUMBER = Pattern.compile("(-?[\\d.]+(?:,\\d+)?)");
    private static final Pattern LEVEL = Pattern.compile(
            "•\\s*([\\d.]+(?:,\\d+)?)\\$\\s*pro Item\\s*-\\s*([\\d.]+)x\\s*\\((\\d+)");

    public static MarketPage parse(String title, List<SlotInfo> slots) {
        if (title == null || !SlotInfo.strip(title).contains("Marktplatz")) return null;
        Map<String, SlotInfo> byName = new HashMap<>();
        for (SlotInfo s : slots) byName.putIfAbsent(s.name(), s);

        if (byName.containsKey("Sofort kaufen") && byName.containsKey("Sofort verkaufen")) {
            return parseDetail(slots, byName);
        }
        if (byName.containsKey("Suche") && byName.containsKey("Deine Aufträge")) {
            return parseOverview(slots, byName);
        }
        if (byName.containsKey("Anzahl setzen") && slots.stream().anyMatch(MarketPageParser::isAmountChoice)) {
            return parseAmount(slots, byName);
        }
        if (byName.containsKey("Preis setzen") || byName.containsKey("Bestes Angebot")) {
            return parsePrice(slots, byName);
        }
        SlotInfo filter = byName.get("Filter");
        if (filter != null && filter.loreContains("Einsammelbar") && byName.containsKey("Zurück")) {
            return parseOrders(slots, byName, filter);
        }
        MarketPage.Confirm c = parseConfirm(slots, byName);
        if (c != null) return c;
        return null;
    }

    private static final java.util.Set<String> AMOUNT_HINTS = java.util.Set.of("Ein Item", "Ein Stack", "Ganzes Inventar");

    private static boolean isAmountChoice(SlotInfo s) {
        return !s.lore().isEmpty() && AMOUNT_HINTS.contains(s.lore().get(0));
    }

    /** "Kaufauftrag" / "Verkaufsangebot" aus der ersten Lore-Zeile eines Knopfes. */
    private static String orderType(List<SlotInfo> slots) {
        for (SlotInfo s : slots) {
            for (String l : s.lore()) {
                if (l.equals("Kaufauftrag") || l.equals("Verkaufsangebot")) return l;
            }
        }
        return null;
    }

    private static MarketPage.Amount parseAmount(List<SlotInfo> slots, Map<String, SlotInfo> byName) {
        List<MarketPage.AmountChoice> choices = new ArrayList<>();
        for (SlotInfo s : slots) {
            if (isAmountChoice(s)) choices.add(new MarketPage.AmountChoice(s.slot(), s.name(), s.lore().get(0)));
        }
        return new MarketPage.Amount(orderType(slots), choices, button(byName.get("Anzahl setzen")),
                button(byName.get("Zurück")));
    }

    private static MarketPage.Price parsePrice(List<SlotInfo> slots, Map<String, SlotInfo> byName) {
        List<MarketPage.PriceChoice> choices = new ArrayList<>();
        for (SlotInfo s : slots) {
            if (s.isFiller() || s.name().equals("Zurück") || s.name().equals("Preis setzen")) continue;
            if (s.loreStartingWith("Preis:") == null && s.loreStartingWith("Gesamt:") == null) continue;
            Double amount = number(s.loreStartingWith("Anzahl:"));
            List<String> extra = new ArrayList<>();
            for (String l : s.lore()) {
                if (l.startsWith("Niedrigster") || l.startsWith("Höchster") || l.startsWith("Spanne")) extra.add(l);
            }
            choices.add(new MarketPage.PriceChoice(s.slot(), s.name(), amount == null ? null : (long) (double) amount,
                    number(s.loreStartingWith("Preis:")), number(s.loreStartingWith("Gesamt:")), List.copyOf(extra)));
        }
        return new MarketPage.Price(orderType(slots), choices, button(byName.get("Preis setzen")),
                button(byName.get("Zurück")));
    }

    private static MarketPage.Confirm parseConfirm(List<SlotInfo> slots, Map<String, SlotInfo> byName) {
        SlotInfo target = null;
        int buttons = 0;
        for (SlotInfo s : slots) {
            if (s.isFiller()) continue;
            buttons++;
            for (String l : s.lore()) {
                if (l.contains("um zu Bestätigen") || l.contains("zum Bestätigen")) target = s;
            }
        }
        // Nur eindeutige Bestätigungsseiten (Bestätigen-Knopf + höchstens Zurück)
        if (target == null || buttons > 2 || byName.get("Zurück") == null) return null;
        List<String> details = new ArrayList<>();
        String hint = null;
        for (String l : target.lore()) {
            if (l.contains("Bestätigen")) hint = l;
            else if (!l.isBlank() && !l.equals("Kaufauftrag") && !l.equals("Verkaufsangebot")) details.add(l);
        }
        return new MarketPage.Confirm(target.slot(), target.itemId(), target.name(), orderType(slots),
                List.copyOf(details), hint, button(byName.get("Zurück")));
    }

    private static MarketPage.Overview parseOverview(List<SlotInfo> slots, Map<String, SlotInfo> byName) {
        List<MarketPage.Button> cats = new ArrayList<>();
        List<MarketPage.Tile> tiles = new ArrayList<>();
        for (SlotInfo s : slots) {
            if (s.isFiller()) continue;
            String buy = s.loreStartingWith("Kaufpreis:");
            String sell = s.loreStartingWith("Verkaufspreis:");
            boolean noBuy = s.loreContains("Kein Kaufpreis");
            boolean noSell = s.loreContains("Kein Verkaufspreis");
            if (buy != null || sell != null || noBuy || noSell) {
                tiles.add(new MarketPage.Tile(s.slot(), s.itemId(), s.name(), number(buy), number(sell)));
            } else if (s.slot() % 9 == 0 && s.slot() < 54 && !s.name().isBlank()
                    && !isControl(s.name())) {
                cats.add(new MarketPage.Button(s.slot(), s.name()));
            }
        }
        SlotInfo mode = byName.containsKey("Experten Modus") ? byName.get("Experten Modus") : byName.get("Einfacher Modus");
        return new MarketPage.Overview(cats, tiles,
                button(byName.get("Weiter")), button(byName.get("Zurück")),
                button(byName.get("Suche")), button(byName.get("Deine Aufträge")),
                button(byName.get("Sofort verkaufen")), button(mode));
    }

    private static boolean isControl(String name) {
        return switch (name) {
            case "Weiter", "Zurück", "Suche", "Deine Aufträge", "Sofort verkaufen",
                 "Experten Modus", "Einfacher Modus" -> true;
            default -> false;
        };
    }

    private static MarketPage.Detail parseDetail(List<SlotInfo> slots, Map<String, SlotInfo> byName) {
        SlotInfo buy = byName.get("Sofort kaufen");
        SlotInfo sell = byName.get("Sofort verkaufen");
        SlotInfo buyOrder = byName.get("Kaufauftrag erstellen");
        SlotInfo sellOffer = byName.get("Verkaufsangebot erstellen");
        // Das Item selbst: kein Füllglas, kein bekannter Knopf, mittig zwischen den Knöpfen
        SlotInfo item = null;
        for (SlotInfo s : slots) {
            if (s.isFiller() || isDetailControl(s.name())) continue;
            if (item == null || Math.abs(s.slot() - 13) < Math.abs(item.slot() - 13)) item = s;
        }
        String inv = sell.loreStartingWith("Im Inventar:");
        String invValue = sell.loreStartingWith("Inventar Wert:");
        String tax = sell.loreStartingWith("Steuern:");
        return new MarketPage.Detail(
                item == null ? -1 : item.slot(), item == null ? "" : item.itemId(), item == null ? "" : item.name(),
                buy.slot(), number(buy.loreStartingWith("Kaufpreis:")), buy.loreContains("kaufen"),
                sell.slot(), number(sell.loreStartingWith("Verkaufspreis:")), sell.loreContains("verkaufen"),
                inv == null ? null : (long) (double) number(inv), number(invValue),
                tax == null ? null : tax.substring("Steuern:".length()).trim(),
                button(buyOrder), buyOrder == null ? List.of() : levels(buyOrder),
                button(sellOffer), sellOffer == null ? List.of() : levels(sellOffer),
                button(byName.get("Zurück")), button(byName.get("Deine Aufträge")),
                sellOffer != null && !sellOffer.loreContains("Keine Items"));
    }

    private static boolean isDetailControl(String name) {
        return switch (name) {
            case "Sofort kaufen", "Sofort verkaufen", "Kaufauftrag erstellen", "Verkaufsangebot erstellen",
                 "Zurück", "Deine Aufträge" -> true;
            default -> false;
        };
    }

    static List<MarketPage.OrderLevel> levels(SlotInfo s) {
        List<MarketPage.OrderLevel> out = new ArrayList<>();
        for (String l : s.lore()) {
            Matcher m = LEVEL.matcher(l);
            if (m.find()) {
                out.add(new MarketPage.OrderLevel(parseNumber(m.group(1)), (long) parseNumber(m.group(2)),
                        Integer.parseInt(m.group(3))));
            }
        }
        return List.copyOf(out);
    }

    private static final Pattern FILLED = Pattern.compile("([\\d.]+)\\s*/\\s*([\\d.]+)(?:\\s*\\(([\\d.,]+)\\s*%)?");

    private static MarketPage.Orders parseOrders(List<SlotInfo> slots, Map<String, SlotInfo> byName, SlotInfo filter) {
        String current = null;
        List<String> options = new ArrayList<>();
        for (String l : filter.lore()) {
            String t = l.trim();
            if (t.startsWith("»")) {
                current = t.substring(1).trim();
                options.add(current);
            } else if (t.startsWith("•")) options.add(t.substring(1).trim());
        }
        boolean filterCollectible = current != null && current.toLowerCase(java.util.Locale.ROOT).startsWith("einsammel");
        List<MarketPage.Order> orders = new ArrayList<>();
        List<MarketPage.Button> extra = new ArrayList<>();
        SlotInfo back = byName.get("Zurück");
        for (SlotInfo s : slots) {
            if (s.isFiller() || s == filter || s == back) continue;
            if (s.loreStartingWith("Status:") == null && s.loreStartingWith("Erfüllt:") == null) {
                if (!s.name().isBlank()) extra.add(button(s));
                continue;
            }
            String type = null;
            Long filled = null, filledOf = null;
            Double percent = null;
            List<String> other = new ArrayList<>();
            for (String l : s.lore()) {
                if (l.equals("Kaufauftrag") || l.equals("Verkaufsangebot")) type = l;
                else if (l.startsWith("Erfüllt:")) {
                    Matcher m = FILLED.matcher(l.substring("Erfüllt:".length()));
                    if (m.find()) {
                        filled = (long) parseNumber(m.group(1));
                        filledOf = (long) parseNumber(m.group(2));
                        if (m.group(3) != null) percent = parseNumber(m.group(3));
                    }
                } else if (!l.isBlank() && !l.startsWith("Anzahl:") && !l.startsWith("Preis pro Item:")
                        && !l.startsWith("Gesamtwert:") && !l.startsWith("Status:")) other.add(l);
            }
            Double amount = number(s.loreStartingWith("Anzahl:"));
            String status = s.loreStartingWith("Status:");
            orders.add(new MarketPage.Order(s.slot(), s.itemId(), s.name(), type,
                    amount == null ? null : (long) (double) amount, filled, filledOf, percent,
                    number(s.loreStartingWith("Preis pro Item:")), number(s.loreStartingWith("Gesamtwert:")),
                    status == null ? null : status.substring("Status:".length()).trim(), List.copyOf(other),
                    filterCollectible || s.lore().stream().anyMatch(l -> l.toLowerCase(java.util.Locale.ROOT).contains("einsammel"))));
        }
        return new MarketPage.Orders(orders, current, List.copyOf(options), button(filter), button(back), extra);
    }

    private static MarketPage.Button button(SlotInfo s) {
        return s == null ? null : new MarketPage.Button(s.slot(), s.name());
    }

    /** "Kaufpreis: 9.599,7$ pro Item" -> 9599.7; null wenn keine Zahl. */
    static Double number(String line) {
        if (line == null) return null;
        int colon = line.indexOf(':');
        Matcher m = NUMBER.matcher(colon >= 0 ? line.substring(colon + 1) : line);
        return m.find() ? parseNumber(m.group(1)) : null;
    }

    static double parseNumber(String s) {
        return Double.parseDouble(s.replace(".", "").replace(',', '.'));
    }
}
