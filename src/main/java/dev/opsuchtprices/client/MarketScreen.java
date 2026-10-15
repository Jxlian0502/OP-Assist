package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.market.MarketEntry;
import dev.opsuchtprices.market.MarketHistory;
import dev.opsuchtprices.market.MarketService;
import dev.opsuchtprices.market.MarketStats;
import dev.opsuchtprices.util.PriceFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Übersicht über den OP SUCHT Markt (Orderbuch) mit Suche, Kategorie-Filter,
 * Sortierung, Seiten und einer Statistik-Ansicht. Reine Anzeige: kein Handel,
 * keine Items werden bewegt.
 */
public final class MarketScreen extends Screen {

    private enum Tab { OFFERS, STATS }

    private enum Sort {
        NAME("Name A-Z"),
        BUY_ASC("Kaufpreis ↑"),
        BUY_DESC("Kaufpreis ↓"),
        SELL_ASC("Verkaufspreis ↑"),
        SELL_DESC("Verkaufspreis ↓"),
        ORDERS_DESC("Meiste Orders"),
        ORDERS_ASC("Wenigste Orders"),
        SPREAD_DESC("Größte Spanne");
        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    // Farben (ARGB)
    private static final int C_BORDER = 0xFF8A6A1F;
    private static final int C_PANEL = 0xE8101018;
    private static final int C_SECTION = 0x30FFFFFF;
    private static final int C_ROW_A = 0x38FFFFFF;
    private static final int C_ROW_B = 0x1CFFFFFF;
    private static final int C_ROW_HOVER = 0x60FFD24A;
    private static final int C_TITLE = 0xFFFFC94A;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFFA8A8A8;
    private static final int C_DIM = 0xFF6E6E6E;
    private static final int C_BUY = 0xFF7CDB6A;
    private static final int C_SELL = 0xFFFFB44A;
    private static final int C_ERROR = 0xFFFF6B6B;

    private static final int ROW_H = 22;

    private final MarketService service;
    private final ModConfig config;
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    private Tab tab = Tab.OFFERS;
    private Sort sort = Sort.NAME;
    private String category = null; // null = alle
    private int page = 0;
    private List<MarketEntry> rows = List.of();
    private MarketService.Snapshot shownSnapshot;

    private EditBox search;
    private Button tabOffers, tabStats, categoryButton, sortButton, refreshButton, prevButton, nextButton;
    private int px, py, pw, ph, listY, listH, rowsPerPage;
    private int colBuy, colSell, colOrders;

    public MarketScreen(MarketService service, ModConfig config) {
        super(Component.literal("OP SUCHT Markt"));
        this.service = service;
        this.config = config;
    }

    // ---------------- Aufbau ----------------

    @Override
    protected void init() {
        service.refreshIfStale();
        pw = Math.min(640, this.width - 16);
        ph = this.height - 16;
        px = (this.width - pw) / 2;
        py = 8;

        tabOffers = addRenderableWidget(Button.builder(Component.literal("Angebote"), b -> setTab(Tab.OFFERS))
                .bounds(px + 8, py + 22, 80, 18).build());
        tabStats = addRenderableWidget(Button.builder(Component.literal("Statistik"), b -> setTab(Tab.STATS))
                .bounds(px + 90, py + 22, 80, 18).build());
        refreshButton = addRenderableWidget(Button.builder(Component.literal("Aktualisieren"), b -> {
            if (!service.refreshNow()) {
                b.setTooltip(Tooltip.create(Component.literal("Gerade erst geladen, bitte kurz warten.")));
            }
        }).bounds(px + pw - 8 - 90, py + 22, 90, 18).build());

        int y2 = py + 44;
        int searchW = pw - 16 - 120 - 120 - 8;
        String prev = search == null ? "" : search.getValue();
        search = new EditBox(this.font, px + 8, y2 + 1, searchW, 16, Component.literal("Suche"));
        search.setMaxLength(48);
        search.setHint(Component.literal("Item suchen…").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(prev);
        search.setResponder(t -> {
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);
        categoryButton = addRenderableWidget(Button.builder(Component.empty(), b -> nextCategory())
                .bounds(px + 8 + searchW + 4, y2, 120, 18).build());
        sortButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            page = 0;
            rebuild();
        }).bounds(px + pw - 8 - 120, y2, 120, 18).build());

        int footY = py + ph - 24;
        prevButton = addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            page = Math.max(0, page - 1);
            updateWidgets();
        }).bounds(px + pw / 2 - 95, footY, 20, 18).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            page = Math.min(pageCount() - 1, page + 1);
            updateWidgets();
        }).bounds(px + pw / 2 + 75, footY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
                .bounds(px + pw - 8 - 70, footY, 70, 18).build());

        listY = y2 + 24 + 12; // Platz für Spaltenköpfe
        listH = footY - 6 - listY;
        rowsPerPage = Math.max(1, listH / ROW_H);
        colOrders = px + pw - 14;
        colSell = colOrders - 52;
        colBuy = colSell - 78;

        rebuild();
        updateWidgets();
    }

    private void setTab(Tab t) {
        tab = t;
        updateWidgets();
    }

    private void nextCategory() {
        List<String> cats = categories();
        if (category == null) {
            category = cats.isEmpty() ? null : cats.get(0);
        } else {
            int i = cats.indexOf(category);
            category = i < 0 || i + 1 >= cats.size() ? null : cats.get(i + 1);
        }
        page = 0;
        rebuild();
    }

    private List<String> categories() {
        MarketService.Snapshot s = service.snapshot();
        Set<String> out = new LinkedHashSet<>();
        if (s != null) for (MarketEntry e : s.entries()) out.add(e.category());
        return new ArrayList<>(out);
    }

    private void updateWidgets() {
        boolean offers = tab == Tab.OFFERS;
        search.visible = offers;
        categoryButton.visible = offers;
        sortButton.visible = offers;
        prevButton.visible = offers;
        nextButton.visible = offers;
        tabOffers.active = !offers;
        tabStats.active = offers;
        categoryButton.setMessage(Component.literal("Kategorie: " + (category == null ? "Alle" : category)));
        sortButton.setMessage(Component.literal(sort.label));
        prevButton.active = page > 0;
        nextButton.active = page < pageCount() - 1;
        if (!offers) setFocused(null);
    }

    /** Filter und Sortierung auf die geladenen Daten anwenden (nur bei Änderungen, nicht pro Frame). */
    private void rebuild() {
        MarketService.Snapshot s = service.snapshot();
        shownSnapshot = s;
        if (s == null) {
            rows = List.of();
            updateWidgets();
            return;
        }
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<MarketEntry> out = new ArrayList<>();
        for (MarketEntry e : s.entries()) {
            if (category != null && !category.equals(e.category())) continue;
            if (!q.isEmpty() && !nameOf(e.material()).toLowerCase(Locale.ROOT).contains(q)
                    && !e.material().toLowerCase(Locale.ROOT).replace('_', ' ').contains(q.replace('_', ' '))) continue;
            out.add(e);
        }
        Comparator<MarketEntry> byName = Comparator.comparing(e -> nameOf(e.material()).toLowerCase(Locale.ROOT));
        Comparator<MarketEntry> c = switch (sort) {
            case NAME -> byName;
            case BUY_ASC -> nullsLast(MarketEntry::buyPrice, false).thenComparing(byName);
            case BUY_DESC -> nullsLast(MarketEntry::buyPrice, true).thenComparing(byName);
            case SELL_ASC -> nullsLast(MarketEntry::sellPrice, false).thenComparing(byName);
            case SELL_DESC -> nullsLast(MarketEntry::sellPrice, true).thenComparing(byName);
            case ORDERS_DESC -> Comparator.comparingInt(MarketEntry::totalOrders).reversed().thenComparing(byName);
            case ORDERS_ASC -> Comparator.comparingInt(MarketEntry::totalOrders).thenComparing(byName);
            case SPREAD_DESC -> nullsLast(MarketEntry::spread, true).thenComparing(byName);
        };
        out.sort(c);
        rows = out;
        page = Math.min(page, pageCount() - 1);
        updateWidgets();
    }

    private static Comparator<MarketEntry> nullsLast(java.util.function.Function<MarketEntry, Double> f, boolean desc) {
        Comparator<Double> base = desc ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(f, Comparator.nullsLast(base));
    }

    private int pageCount() {
        return Math.max(1, (rows.size() + rowsPerPage - 1) / Math.max(1, rowsPerPage));
    }

    @Override
    public void tick() {
        service.refreshIfStale(); // lädt nur, wenn das Intervall abgelaufen ist
        if (service.snapshot() != shownSnapshot) {
            rebuild(); // neue Daten übernehmen, Seite/Filter bleiben erhalten
        }
    }

    // ---------------- Item-Namen und Bilder ----------------

    private ItemStack stackOf(String material) {
        return stacks.computeIfAbsent(material, m -> {
            Identifier id = Identifier.tryParse("minecraft:" + m.toLowerCase(Locale.ROOT));
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
            return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
        });
    }

    /** Übersetzter Minecraft-Name (z. B. "Diamantblock"), sonst lesbar gemachte ID. */
    private String nameOf(String material) {
        return names.computeIfAbsent(material, m -> {
            ItemStack s = stackOf(m);
            if (!s.is(Items.BARRIER)) return s.getHoverName().getString();
            String t = m.toLowerCase(Locale.ROOT).replace('_', ' ');
            return t.isEmpty() ? m : Character.toUpperCase(t.charAt(0)) + t.substring(1);
        });
    }

    // ---------------- Darstellung ----------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER);
        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.drawString(this.font, "OP SUCHT Markt", px + 8, py + 7, C_TITLE);
        drawStatus(g);

        Instant now = Instant.now();
        MarketEntry hovered = null;
        MarketService.Snapshot s = service.snapshot();

        if (s == null) {
            String msg = service.lastError() != null ? "Laden fehlgeschlagen: " + service.lastError()
                    : "Marktdaten werden geladen…";
            g.drawCenteredString(this.font, msg, px + pw / 2, py + ph / 2 - 4,
                    service.lastError() != null ? C_ERROR : C_MUTED);
        } else if (tab == Tab.OFFERS) {
            hovered = renderOffers(g, mx, my);
        } else {
            hovered = renderStats(g, s.stats(), mx, my);
        }

        super.render(g, mx, my, delta);

        if (tab == Tab.OFFERS && s != null) {
            String p = "Seite " + (page + 1) + "/" + pageCount() + " · " + rows.size() + " Items";
            g.drawCenteredString(this.font, p, px + pw / 2, py + ph - 19, C_MUTED);
        }
        if (hovered != null) {
            g.setComponentTooltipForNextFrame(this.font, tooltip(hovered, now), mx, my);
        }
    }

    private void drawStatus(GuiGraphics g) {
        String text;
        int color = C_MUTED;
        MarketService.Snapshot s = service.snapshot();
        if (service.isLoading()) {
            text = "Lädt…";
        } else if (service.lastError() != null && s != null) {
            text = "Fehler: " + service.lastError() + " (zeige ältere Daten)";
            color = C_ERROR;
        } else if (s != null) {
            text = "Stand: " + PriceFormat.ago(s.loadedAt(), Instant.now());
        } else {
            text = "";
        }
        text = this.font.plainSubstrByWidth(text, pw - 120);
        g.drawString(this.font, text, px + pw - 8 - this.font.width(text), py + 7, color);
    }

    private MarketEntry renderOffers(GuiGraphics g, int mx, int my) {
        int left = px + 6, right = px + pw - 6;
        int headY = listY - 11;
        g.drawString(this.font, "Item", left + 24, headY, C_DIM);
        drawRight(g, "Kaufpreis", colBuy, headY, C_DIM);
        drawRight(g, "Verkaufspreis", colSell, headY, C_DIM);
        drawRight(g, "Orders", colOrders, headY, C_DIM);

        if (rows.isEmpty()) {
            g.drawCenteredString(this.font, "Keine Treffer.", px + pw / 2, listY + 20, C_MUTED);
            return null;
        }
        MarketEntry hovered = null;
        int start = page * rowsPerPage;
        for (int i = 0; i < rowsPerPage && start + i < rows.size(); i++) {
            MarketEntry e = rows.get(start + i);
            int y = listY + i * ROW_H;
            boolean hover = mx >= left && mx < right && my >= y && my < y + ROW_H - 1;
            if (hover) hovered = e;
            g.fill(left, y, right, y + ROW_H - 1, hover ? C_ROW_HOVER : (i % 2 == 0 ? C_ROW_A : C_ROW_B));
            g.renderItem(stackOf(e.material()), left + 3, y + 3);

            int nameMax = colBuy - 62 - (left + 24);
            g.drawString(this.font, this.font.plainSubstrByWidth(nameOf(e.material()), nameMax), left + 24, y + 3, C_TEXT);
            g.drawString(this.font, this.font.plainSubstrByWidth(e.category(), nameMax), left + 24, y + 12, C_DIM);

            drawRight(g, priceOrDash(e.buyPrice()), colBuy, y + 7, e.buyPrice() == null ? C_DIM : C_BUY);
            drawRight(g, priceOrDash(e.sellPrice()), colSell, y + 7, e.sellPrice() == null ? C_DIM : C_SELL);
            drawRight(g, String.valueOf(e.totalOrders()), colOrders, y + 7, e.totalOrders() == 0 ? C_DIM : C_TEXT);
        }
        return hovered;
    }

    private MarketEntry renderStats(GuiGraphics g, MarketStats st, int mx, int my) {
        int top = py + 48;
        int colW = (pw - 24) / 2;
        int lx = px + 8, rx = px + 16 + colW;
        String cur = config.currencyLabel;

        // Linke Spalte: Kennzahlen
        g.fill(lx, top, lx + colW, py + ph - 30, C_SECTION);
        int y = top + 6;
        g.drawString(this.font, "Marktübersicht", lx + 6, y, C_TITLE);
        y += 14;
        y = kv(g, lx, colW, y, "Items im Markt", String.valueOf(st.itemCount()));
        y = kv(g, lx, colW, y, "mit Kaufpreis", String.valueOf(st.itemsWithBuy()));
        y = kv(g, lx, colW, y, "mit Verkaufspreis", String.valueOf(st.itemsWithSell()));
        y = kv(g, lx, colW, y, "ohne aktive Orders", String.valueOf(st.itemsWithoutOrders()));
        y += 4;
        y = kv(g, lx, colW, y, "Aktive Orders (Kauf)", PriceFormat.number(st.totalBuyOrders()));
        y = kv(g, lx, colW, y, "Aktive Orders (Verkauf)", PriceFormat.number(st.totalSellOrders()));
        y += 4;
        if (st.cheapest() != null) {
            y = kv(g, lx, colW, y, "Günstigstes Item", nameOf(st.cheapest().material()));
            y = kv(g, lx, colW, y, "", PriceFormat.price(st.cheapest().buyPrice(), cur) + " /Stk.");
            y = kv(g, lx, colW, y, "Teuerstes Item", nameOf(st.mostExpensive().material()));
            y = kv(g, lx, colW, y, "", PriceFormat.price(st.mostExpensive().buyPrice(), cur) + " /Stk.");
            y = kv(g, lx, colW, y, "Median Kaufpreis", PriceFormat.price(st.medianBuyPrice(), cur) + " /Stk.");
        }
        y += 8;
        int noteW = colW - 12;
        for (var line : this.font.split(Component.literal(
                "Nicht verfügbar: Verkäufer, angebotene Mengen und Gesamtwert. Die Markt-API liefert pro Item "
                        + "nur Preis und Anzahl der Orders."), noteW)) {
            if (y > py + ph - 40) break;
            g.drawString(this.font, line, lx + 6, y, C_DIM);
            y += 10;
        }

        // Rechte Spalte: Ranglisten mit Bildern
        MarketEntry hovered = null;
        int ry = top;
        int sectionH = (py + ph - 30 - top - 8) / 3;
        hovered = topList(g, rx, ry, colW, sectionH, "Niedrigste Kaufpreise", st.cheapestTop(), true, mx, my, hovered);
        hovered = topList(g, rx, ry + sectionH + 4, colW, sectionH, "Höchste Kaufpreise", st.expensiveTop(), true, mx, my, hovered);
        hovered = topList(g, rx, ry + 2 * (sectionH + 4), colW, sectionH, "Meiste aktive Orders", st.mostOrdersTop(), false, mx, my, hovered);
        return hovered;
    }

    private MarketEntry topList(GuiGraphics g, int x, int y, int w, int h, String title, List<MarketEntry> list,
                                boolean price, int mx, int my, MarketEntry hovered) {
        g.fill(x, y, x + w, y + h, C_SECTION);
        g.drawString(this.font, title, x + 6, y + 5, C_TITLE);
        int ly = y + 17;
        int lineH = Math.max(12, Math.min(18, (h - 20) / Math.max(1, list.size())));
        for (MarketEntry e : list) {
            if (ly + lineH > y + h) break;
            if (mx >= x && mx < x + w && my >= ly && my < ly + lineH) {
                hovered = e;
                g.fill(x + 2, ly, x + w - 2, ly + lineH, C_ROW_HOVER);
            }
            if (lineH >= 16) {
                g.renderItem(stackOf(e.material()), x + 4, ly);
            }
            int tx = lineH >= 16 ? x + 24 : x + 6;
            String value = price ? PriceFormat.price(e.buyPrice(), config.currencyLabel) : e.totalOrders() + " Orders";
            int vw = this.font.width(value);
            g.drawString(this.font, this.font.plainSubstrByWidth(nameOf(e.material()), x + w - 8 - vw - 6 - tx),
                    tx, ly + (lineH - 8) / 2, C_TEXT);
            g.drawString(this.font, value, x + w - 6 - vw, ly + (lineH - 8) / 2, price ? C_BUY : C_MUTED);
            ly += lineH;
        }
        return hovered;
    }

    private int kv(GuiGraphics g, int x, int w, int y, String k, String v) {
        g.drawString(this.font, k, x + 6, y, C_MUTED);
        String vv = this.font.plainSubstrByWidth(v, w - 12 - this.font.width(k) - 6);
        g.drawString(this.font, vv, x + w - 6 - this.font.width(vv), y, C_TEXT);
        return y + 11;
    }

    private void drawRight(GuiGraphics g, String s, int rightX, int y, int color) {
        g.drawString(this.font, s, rightX - this.font.width(s), y, color);
    }

    private String priceOrDash(Double p) {
        return p == null ? "–" : PriceFormat.price(p, config.currencyLabel);
    }

    private List<Component> tooltip(MarketEntry e, Instant now) {
        String cur = config.currencyLabel;
        List<Component> l = new ArrayList<>();
        l.add(Component.literal(nameOf(e.material())).withStyle(ChatFormatting.GOLD));
        l.add(Component.literal(e.category() + " · " + e.material()).withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.empty());
        l.add(line("Kaufpreis: ", e.buyPrice() == null ? "keine Orders"
                : PriceFormat.price(e.buyPrice(), cur) + " /Stk. · " + e.buyOrders() + " Orders", ChatFormatting.GREEN));
        l.add(line("Verkaufspreis: ", e.sellPrice() == null ? "keine Orders"
                : PriceFormat.price(e.sellPrice(), cur) + " /Stk. · " + e.sellOrders() + " Orders", ChatFormatting.GOLD));
        if (e.spread() != null) {
            l.add(line("Spanne: ", PriceFormat.price(e.spread(), cur), ChatFormatting.WHITE));
        }
        l.add(Component.empty());
        MarketHistory h = service.history(e.material());
        if (h == null) {
            l.add(Component.literal("Verlauf wird geladen…").withStyle(ChatFormatting.DARK_GRAY));
        } else if (h.avg24h() == null) {
            l.add(Component.literal("Keine Verkäufe in den letzten 24 Std.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            l.add(Component.literal("Letzte 24 Stunden:").withStyle(ChatFormatting.GRAY));
            l.add(line(" Schnitt: ", PriceFormat.price(h.avg24h(), cur) + " /Stk.", ChatFormatting.YELLOW));
            l.add(line(" Spanne: ", PriceFormat.price(h.min24h(), cur) + " – " + PriceFormat.price(h.max24h(), cur),
                    ChatFormatting.WHITE));
            l.add(line(" Gehandelt: ", PriceFormat.number(h.items24h()) + " Items in "
                    + PriceFormat.number(h.trades24h()) + " Transaktionen", ChatFormatting.WHITE));
            if (h.avg7d() != null) {
                l.add(line(" Schnitt 7 Tage: ", PriceFormat.price(h.avg7d(), cur) + " /Stk.", ChatFormatting.WHITE));
            }
        }
        return l;
    }

    private static Component line(String label, String value, ChatFormatting color) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(color));
    }

    // ---------------- Eingabe ----------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.OFFERS && mouseY >= listY && mouseY < listY + listH) {
            page = (int) Math.max(0, Math.min(pageCount() - 1, page - Math.signum(scrollY)));
            updateWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
