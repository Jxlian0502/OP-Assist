package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.market.MarketHistory;
import dev.opsuchtprices.market.MarketService;
import dev.opsuchtprices.overlay.MarketPage;
import dev.opsuchtprices.overlay.MarketPageParser;
import dev.opsuchtprices.overlay.SlotInfo;
import dev.opsuchtprices.util.PriceFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eigene Oberfläche über dem /markt-Menü von OP SUCHT.
 *
 * Das Server-Menü bleibt im Hintergrund offen; das Overlay zeichnet nur anders.
 * Jeder Klick im Overlay löst genau EINEN Klick auf den passenden Slot des
 * Server-Menüs aus – keine Automatik, keine Klickfolgen. "1 kaufen" und
 * "1 verkaufen" brauchen im Overlay eine zweite Bestätigung, weil der Server
 * diese Aktionen sofort ausführt.
 *
 * Erkannt werden Übersicht, Item-Seite, Auftrag erstellen (Menge, Preis, Bestätigen)
 * und "Deine Aufträge". Suche und Verkaufsfenster erscheinen im Original.
 */
public final class MarketOverlay {
    private MarketOverlay() {}

    // Farben (ARGB)
    private static final int C_DIM = 0xC0000000;
    private static final int C_BORDER = 0xFF8A6A1F;
    private static final int C_PANEL = 0xF0101018;
    private static final int C_SECTION = 0x30FFFFFF;
    private static final int C_CARD = 0x38FFFFFF;
    private static final int C_HOVER = 0x60FFD24A;
    private static final int C_TITLE = 0xFFFFC94A;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFFA8A8A8;
    private static final int C_FAINT = 0xFF6E6E6E;
    private static final int C_BUY = 0xFF7CDB6A;
    private static final int C_SELL = 0xFFFFB44A;
    private static final int C_BTN = 0xFF2A2A36;
    private static final int C_BTN_HOVER = 0xFF4A4A5E;
    private static final int C_BTN_OFF = 0xFF1C1C22;
    private static final int C_BTN_ARMED = 0xFF8A2A2A;

    private static MarketService market;
    private static ModConfig config;

    /** Benutzer hat "Original-Menü" gewählt (gilt bis das Menü geschlossen wird). */
    private static boolean showOriginal;

    // Cache der erkannten Seite
    private static AbstractContainerMenu cachedMenu;
    private static int cachedState = -1;
    private static MarketPage cachedPage;

    // Klickflächen des zuletzt gezeichneten Frames
    private record Target(int x, int y, int w, int h, String label, Runnable action) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final List<Target> targets = new ArrayList<>();
    private static long lastClickMs;
    /** Bestätigung für Sofortkauf/-verkauf: Schlüssel der scharf geschalteten Aktion und Ablaufzeit. */
    private static String armedKey;
    private static long armedUntil;
    private static String status;
    /** Scrollposition der Item-Liste (Pixel) und Bereich der Liste aus dem letzten Frame. */
    private static int scroll;
    private static int[] lastListArea;
    private static AbstractContainerMenu scrollMenu;
    private static long statusUntil;

    public static void init(MarketService marketService, ModConfig modConfig) {
        market = marketService;
        config = modConfig;
    }

    /** Jeden Client-Tick: Original-Modus zurücksetzen, wenn kein Menü mehr offen ist. */
    public static void tick(Minecraft mc) {
        collectTick(mc);
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            showOriginal = false;
            armedKey = null;
            cachedMenu = null;
        }
    }

    /** Server-Nachrichten zum Handel kurz im Overlay anzeigen. */
    public static void onServerMessage(Component message, boolean overlay) {
        String t = SlotInfo.strip(message.getString());
        if (collecting) {
            String lc = t.toLowerCase(Locale.ROOT);
            if (lc.contains("voll") || lc.contains("kein platz") || lc.contains("nicht genug") || lc.contains("fehler")) {
                stopCollect(t.startsWith("OPSUCHT »") ? t.substring("OPSUCHT »".length()).trim() : t);
                return;
            }
        }
        if (!t.startsWith("OPSUCHT »")) return;
        String l = t.toLowerCase(Locale.ROOT);
        if (l.contains("auftrag") || l.contains("angebot") || l.contains("erhalten") || l.contains("geld")
                || l.contains("nicht genug")) {
            status = t.substring("OPSUCHT »".length()).trim();
            statusUntil = System.currentTimeMillis() + 8000;
        }
    }

    // ---------------- Erkennung ----------------

    private static MarketPage page(AbstractContainerScreen<?> screen) {
        if (config == null || !config.marketOverlay || showOriginal) return null;
        AbstractContainerMenu menu = screen.getMenu();
        int hash = contentHash(menu);
        if (menu == cachedMenu && hash == cachedState) return cachedPage;
        List<SlotInfo> slots = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container instanceof Inventory) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = new ArrayList<>();
            ItemLore il = st.get(DataComponents.LORE);
            if (il != null) for (Component c : il.lines()) lore.add(SlotInfo.strip(c.getString()));
            slots.add(new SlotInfo(i, BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(),
                    SlotInfo.strip(st.getHoverName().getString()), lore));
        }
        cachedMenu = menu;
        cachedState = hash;
        cachedPage = MarketPageParser.parse(screen.getTitle().getString(), slots);
        return cachedPage;
    }

    /** Fingerabdruck des Menü-Inhalts (ohne Spielerinventar): ändert sich bei jeder Antwort des Servers. */
    private static int contentHash(AbstractContainerMenu menu) {
        int h = 1;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            ItemStack st = s.getItem();
            h = 31 * h + (st.isEmpty() ? 0 : ItemStack.hashItemAndComponents(st) * 31 + st.getCount());
        }
        return h;
    }

    // ---------------- Alle einsammeln ----------------
    // Ein Klick des Spielers startet eine Folge von Einzelklicks auf einsammelbare Aufträge.
    // Nach jedem Klick wird auf die Antwort des Servers gewartet (Menü-Inhalt ändert sich);
    // bei anderem Menü, Server-Fehlermeldung, ausbleibender Antwort oder Klick des Spielers wird gestoppt.

    private static final long COLLECT_GAP_MS = 350;
    private static final long COLLECT_TIMEOUT_MS = 2500;
    private static boolean collecting;
    private static boolean collectWaiting;
    private static int collectLeft;
    private static int collected;
    private static int collectHash;
    private static AbstractContainerMenu collectMenu;
    private static long collectNextAt;
    private static long collectWaitUntil;

    private static void startCollect(AbstractContainerMenu menu, int count) {
        collecting = true;
        collectWaiting = false;
        collectLeft = count;
        collected = 0;
        collectMenu = menu;
        collectNextAt = 0;
        setStatus("Sammle ein…");
    }

    private static void stopCollect(String reason) {
        if (!collecting) return;
        collecting = false;
        collectWaiting = false;
        setStatus(reason == null ? collected + " eingesammelt" : "Stopp: " + reason + " (" + collected + " eingesammelt)");
    }

    private static void setStatus(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 8000;
    }

    private static void collectTick(Minecraft mc) {
        if (!collecting) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> scr) || showOriginal) {
            stopCollect("Menü zu");
            return;
        }
        long now = System.currentTimeMillis();
        AbstractContainerMenu menu = scr.getMenu();
        if (collectWaiting) {
            if (menu != collectMenu || contentHash(menu) != collectHash) {
                collectWaiting = false;
                collected++;
                collectNextAt = now + COLLECT_GAP_MS;
            } else if (now > collectWaitUntil) {
                stopCollect("keine Antwort");
                return;
            } else {
                return;
            }
        }
        if (now < collectNextAt) return;
        if (!(page(scr) instanceof MarketPage.Orders o)) {
            stopCollect("anderes Menü");
            return;
        }
        if (collectLeft <= 0) {
            stopCollect(null);
            return;
        }
        MarketPage.Order next = null;
        for (MarketPage.Order od : o.orders()) {
            if (od.collectible()) {
                next = od;
                break;
            }
        }
        if (next == null) {
            stopCollect(null);
            return;
        }
        collectMenu = menu;
        collectHash = contentHash(menu);
        collectWaiting = true;
        collectWaitUntil = now + COLLECT_TIMEOUT_MS;
        collectLeft--;
        if (!sendClick(menu, next.slot(), 0)) stopCollect("Menü zu");
    }

    /** Für Tests. */
    public static boolean isCollecting() {
        return collecting;
    }

    /** Ist das Overlay für diesen Bildschirm aktiv? */
    public static boolean isActive(AbstractContainerScreen<?> screen) {
        return page(screen) != null;
    }

    // ---------------- Zeichnen ----------------

    /** Zeichnet das Overlay. Liefert false, wenn das Original-Menü gezeichnet werden soll. */
    public static boolean render(AbstractContainerScreen<?> screen, GuiGraphics g, int mx, int my) {
        MarketPage p = page(screen);
        if (p == null) return false;
        targets.clear();
        lastListArea = null;
        if (screen.getMenu() != scrollMenu) {
            scrollMenu = screen.getMenu();
            scroll = 0;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = Math.min(640, screen.width - 16);
        int h = Math.min(400, screen.height - 16);
        int x = (screen.width - w) / 2;
        int y = (screen.height - h) / 2;

        g.fill(0, 0, screen.width, screen.height, C_DIM);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, C_BORDER);
        g.fill(x, y, x + w, y + h, C_PANEL);
        g.drawString(font, "OPSUCHT » Marktplatz", x + 8, y + 7, C_TITLE);
        button(g, font, x + w - 98, y + 4, 90, 14, "Original-Menü", true, false, mx, my, () -> {
            showOriginal = true;
        });

        ItemStack hoverStack = null;
        if (p instanceof MarketPage.Overview o) {
            hoverStack = renderOverview(screen, g, font, o, x, y + 22, w, h - 22, mx, my);
        } else if (p instanceof MarketPage.Detail d) {
            hoverStack = renderDetail(screen, g, font, d, x, y + 22, w, h - 22, mx, my);
        } else if (p instanceof MarketPage.Amount a) {
            hoverStack = renderAmount(screen, g, font, a, x, y + 22, w, h - 22, mx, my);
        } else if (p instanceof MarketPage.Price pr) {
            hoverStack = renderPrice(screen, g, font, pr, x, y + 22, w, h - 22, mx, my);
        } else if (p instanceof MarketPage.Confirm c) {
            hoverStack = renderConfirm(screen, g, font, c, x, y + 22, w, h - 22, mx, my);
        } else if (p instanceof MarketPage.Orders o) {
            hoverStack = renderOrders(screen, g, font, o, x, y + 22, w, h - 22, mx, my);
        }

        if (status != null && System.currentTimeMillis() < statusUntil) {
            // in der Kopfzeile zwischen Titel und "Original-Menü", damit nichts verdeckt wird
            int sx = x + 8 + font.width("OPSUCHT » Marktplatz") + 12, sw = x + w - 104 - sx;
            String s = font.plainSubstrByWidth(status, sw - 8);
            g.fill(sx, y + 3, sx + font.width(s) + 8, y + 17, 0xA0000000);
            g.drawString(font, s, sx + 4, y + 6, C_BUY);
        }
        if (hoverStack != null) {
            g.setTooltipForNextFrame(font, hoverStack, mx, my);
        }
        return true;
    }

    private static ItemStack renderOverview(AbstractContainerScreen<?> screen, GuiGraphics g, Font font,
                                            MarketPage.Overview o, int x, int y, int w, int h, int mx, int my) {
        AbstractContainerMenu menu = screen.getMenu();
        ItemStack hovered = null;

        // Seitenleiste: Kategorien, darunter Funktionen
        int sideW = Math.min(112, w / 4);
        int footH = 20;
        g.fill(x + 6, y, x + 6 + sideW, y + h - 4, C_SECTION);
        List<MarketPage.Button> funcs = new ArrayList<>();
        for (MarketPage.Button b : new MarketPage.Button[]{o.search(), o.myOrders(), o.sellItems(), o.mode()}) {
            if (b != null) funcs.add(b);
        }
        int funcH = 15;
        int funcBlock = funcs.size() * (funcH + 2) + 4;
        int n = Math.max(1, o.categories().size());
        int catH = Math.max(13, Math.min(22, (h - 8 - funcBlock) / n));
        int by = y + 3;
        for (MarketPage.Button c : o.categories()) {
            ItemStack icon = stack(menu, c.slot());
            iconButton(g, font, x + 9, by, sideW - 6, catH - 2, icon, c.label(), mx, my, () -> click(menu, c.slot(), 0));
            by += catH;
        }
        by = y + h - 4 - funcBlock + 2;
        for (MarketPage.Button b : funcs) {
            String label = switch (b.label()) {
                case "Sofort verkaufen" -> "Items verkaufen";
                case "Deine Aufträge" -> "Deine Aufträge";
                case "Experten Modus" -> "Modus: Einfach";
                case "Einfacher Modus" -> "Modus: Experte";
                default -> b.label();
            };
            button(g, font, x + 9, by, sideW - 6, funcH, label, true, false, mx, my, () -> click(menu, b.slot(), 0));
            by += funcH + 2;
        }

        // Items als Karten, scrollbar
        int gx = x + sideW + 12, gw = w - sideW - 18;
        int gy = y, gh = h - footH - 4;
        int cardH = 34;
        int minCard = 132;
        int cols = Math.max(1, (gw + 4) / (minCard + 4));
        int cardW = (gw - (cols - 1) * 4) / cols;
        int rows = (o.tiles().size() + cols - 1) / cols;
        int content = rows * (cardH + 3);
        int maxScroll = Math.max(0, content - gh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        lastListArea = new int[]{gx, gy, gw, gh, maxScroll};

        g.enableScissor(gx, gy, gx + gw, gy + gh);
        int i = 0;
        for (MarketPage.Tile t : o.tiles()) {
            int cx = gx + (i % cols) * (cardW + 4);
            int cy = gy + (i / cols) * (cardH + 3) - scroll;
            i++;
            if (cy + cardH < gy || cy > gy + gh) continue;
            boolean inside = my >= gy && my < gy + gh;
            boolean hover = inside && mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH;
            g.fill(cx, cy, cx + cardW, cy + cardH, hover ? C_HOVER : C_CARD);
            ItemStack st = stack(menu, t.slot());
            g.renderItem(st, cx + 4, cy + 9);
            g.drawString(font, font.plainSubstrByWidth(t.name(), cardW - 28), cx + 24, cy + 3, C_TEXT);
            String buy = t.buyPrice() == null ? "–" : PriceFormat.exact(t.buyPrice(), cur());
            String sell = t.sellPrice() == null ? "–" : PriceFormat.exact(t.sellPrice(), cur());
            g.drawString(font, "Kaufen", cx + 24, cy + 13, C_FAINT);
            g.drawString(font, buy, cx + cardW - 4 - font.width(buy), cy + 13, C_BUY);
            g.drawString(font, "Verkaufen", cx + 24, cy + 23, C_FAINT);
            g.drawString(font, sell, cx + cardW - 4 - font.width(sell), cy + 23, C_SELL);
            int top = Math.max(cy, gy), bottom = Math.min(cy + cardH, gy + gh);
            if (bottom > top) {
                int slot = t.slot();
                targets.add(new Target(cx, top, cardW, bottom - top, t.name(), () -> click(menu, slot, 0)));
            }
            if (hover) hovered = st;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, gh * gh / content);
            int barY = gy + (int) ((gh - barH) * (scroll / (double) maxScroll));
            g.fill(gx + gw - 2, gy, gx + gw, gy + gh, 0x30FFFFFF);
            g.fill(gx + gw - 2, barY, gx + gw, barY + barH, C_BORDER);
        }
        if (o.tiles().isEmpty()) {
            g.drawCenteredString(font, "Keine Items auf dieser Seite.", gx + gw / 2, gy + 30, C_MUTED);
        }

        // Fußzeile: Hinweis und Seiten
        int fy = y + h - footH;
        int navW = 0;
        if (o.next() != null) {
            navW += 66;
            button(g, font, x + w - 6 - 62, fy, 62, 16, "Weiter ▶", true, false, mx, my, () -> click(menu, o.next().slot(), 0));
        }
        if (o.previous() != null) {
            button(g, font, x + w - 6 - navW - 62, fy, 62, 16, "◀ Zurück", true, false, mx, my, () -> click(menu, o.previous().slot(), 0));
            navW += 66;
        }
        String hint = font.plainSubstrByWidth("Preise pro Stück · Mausrad zum Scrollen", gw - navW - 6);
        g.drawString(font, hint, gx, fy + 4, C_FAINT);
        return hovered;
    }

    private static ItemStack renderDetail(AbstractContainerScreen<?> screen, GuiGraphics g, Font font,
                                          MarketPage.Detail d, int x, int y, int w, int h, int mx, int my) {
        AbstractContainerMenu menu = screen.getMenu();
        ItemStack hovered = null;
        String cur = cur();

        // Kopf: Item, Preise, Inventar
        g.fill(x + 6, y, x + w - 6, y + 46, C_SECTION);
        ItemStack item = d.itemSlot() >= 0 ? stack(menu, d.itemSlot()) : ItemStack.EMPTY;
        g.pose().pushMatrix();
        g.pose().translate(x + 12, y + 7);
        g.pose().scale(2f, 2f);
        g.renderItem(item, 0, 0);
        g.pose().popMatrix();
        if (mx >= x + 12 && mx < x + 44 && my >= y + 7 && my < y + 39) hovered = item;
        g.drawString(font, d.name(), x + 52, y + 7, C_TITLE);
        g.drawString(font, "Kaufpreis: " + priceOrDash(d.buyPrice(), cur) + " pro Item", x + 52, y + 20, C_BUY);
        g.drawString(font, "Verkaufspreis: " + priceOrDash(d.sellPrice(), cur) + " pro Item", x + 52, y + 32, C_SELL);
        int rx = x + w - 12;
        String inv = d.inInventory() == null ? "Nicht im Inventar" : "Im Inventar: " + PriceFormat.number(d.inInventory());
        g.drawString(font, inv, rx - font.width(inv), y + 7, C_TEXT);
        if (d.inventoryValue() != null) {
            String v = "Wert: " + PriceFormat.exact(d.inventoryValue(), cur);
            g.drawString(font, v, rx - font.width(v), y + 20, C_MUTED);
        }
        if (d.tax() != null) {
            String t = "Steuern: " + d.tax();
            g.drawString(font, t, rx - font.width(t), y + 32, C_FAINT);
        }

        // Aktionen
        int ay = y + 52;
        int bw = (w - 12 - 3 * 4) / 4;
        int bx = x + 6;
        String buyKey = "buy:" + menu.containerId;
        String sellKey = "sell:" + menu.containerId;
        boolean buyArmed = isArmed(buyKey), sellArmed = isArmed(sellKey);
        button(g, font, bx, ay, bw, 20,
                buyArmed ? "Sicher? " + priceOrDash(d.buyPrice(), cur) : "1 kaufen " + priceOrDash(d.buyPrice(), cur),
                d.canBuy(), buyArmed, mx, my, () -> confirmThenClick(buyKey, menu, d.buySlot(), 0));
        button(g, font, bx + bw + 4, ay, bw, 20, "Kaufen: Anzahl…", d.canBuy(), false, mx, my,
                () -> click(menu, d.buySlot(), 1));
        button(g, font, bx + 2 * (bw + 4), ay, bw, 20,
                sellArmed ? "Sicher? " + priceOrDash(d.sellPrice(), cur) : "1 verkaufen " + priceOrDash(d.sellPrice(), cur),
                d.canSell(), sellArmed, mx, my, () -> confirmThenClick(sellKey, menu, d.sellSlot(), 0));
        button(g, font, bx + 3 * (bw + 4), ay, bw, 20, "Verkauf: Anzahl…", d.canSell(), false, mx, my,
                () -> click(menu, d.sellSlot(), 1));

        int ay2 = ay + 24;
        int bw2 = (w - 12 - 3 * 4) / 4;
        if (d.createBuyOrder() != null) {
            button(g, font, bx, ay2, bw2, 16, "Kaufauftrag", true, false, mx, my,
                    () -> click(menu, d.createBuyOrder().slot(), 0));
        }
        if (d.createSellOffer() != null) {
            int sx = bx + bw2 + 4;
            button(g, font, sx, ay2, bw2, 16, "Verkaufsangebot", d.canSellOffer(), false, mx, my,
                    () -> click(menu, d.createSellOffer().slot(), 0));
            if (!d.canSellOffer() && mx >= sx && mx < sx + bw2 && my >= ay2 && my < ay2 + 16) {
                g.setTooltipForNextFrame(font, List.of(
                        Component.literal("Keine Items im Inventar.").getVisualOrderText(),
                        Component.literal("Der Server erlaubt ein Verkaufsangebot nur,").withStyle(ChatFormatting.GRAY).getVisualOrderText(),
                        Component.literal("wenn du das Item im Inventar hast.").withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                        mx, my);
            }
        }
        if (d.myOrders() != null) {
            button(g, font, bx + 2 * (bw2 + 4), ay2, bw2, 16, "Aufträge", true, false, mx, my,
                    () -> click(menu, d.myOrders().slot(), 0));
        }
        if (d.back() != null) {
            button(g, font, bx + 3 * (bw2 + 4), ay2, bw2, 16, "◀ Zurück", true, false, mx, my,
                    () -> click(menu, d.back().slot(), 0));
        }

        // Orderbuch
        int oy = ay2 + 22;
        int colW = (w - 12 - 6) / 2;
        int bottom = y + h - 34;
        orderTable(g, font, x + 6, oy, colW, bottom - oy, "Verkaufsangebote (hier kaufst du)",
                d.sellOffers(), C_BUY, cur);
        orderTable(g, font, x + 12 + colW, oy, colW, bottom - oy, "Kaufaufträge (hier verkaufst du)",
                d.buyOrders(), C_SELL, cur);

        // Verlauf aus der API (letzte 24 Std.)
        String material = d.itemId().contains(":") ? d.itemId().substring(d.itemId().indexOf(':') + 1) : d.itemId();
        MarketHistory hist = market == null || material.isEmpty() ? null : market.history(material);
        String line;
        if (hist == null) {
            line = "Verlauf wird geladen…";
        } else if (hist.avg24h() == null) {
            line = "Keine Markt-Verkäufe in den letzten 24 Std.";
        } else {
            line = "Letzte 24 Std.: Schnitt " + PriceFormat.exact(hist.avg24h(), cur) + " · "
                    + PriceFormat.exact(hist.min24h(), cur) + " – " + PriceFormat.exact(hist.max24h(), cur)
                    + " · " + PriceFormat.number(hist.items24h()) + " Items gehandelt"
                    + (hist.avg7d() == null ? "" : " · Schnitt 7 Tage " + PriceFormat.exact(hist.avg7d(), cur));
        }
        g.drawString(font, font.plainSubstrByWidth(line, w - 16), x + 8, y + h - 30, C_MUTED);
        return hovered;
    }

    // ---------------- Auftrag: Menge -> Preis -> Bestätigen ----------------

    private static String flowTitle(String orderType, int step, String what) {
        String t = orderType == null ? "Anzahl wählen" : orderType + " erstellen";
        return orderType == null ? t : t + " · Schritt " + step + "/3: " + what;
    }

    private static ItemStack renderAmount(AbstractContainerScreen<?> screen, GuiGraphics g, Font font,
                                          MarketPage.Amount a, int x, int y, int w, int h, int mx, int my) {
        AbstractContainerMenu menu = screen.getMenu();
        ItemStack hovered = null;
        g.drawString(font, flowTitle(a.orderType(), 1, "Menge"), x + 8, y + 2, C_TEXT);
        if (a.orderType() == null) {
            g.drawString(font, "Achtung: Der Server kann sofort ausführen, darum mit Bestätigung.", x + 8, y + 14, C_SELL);
        }
        int n = Math.max(1, a.choices().size());
        int gap = 6, cardH = 58;
        int cardW = Math.min(150, (w - 16 - (n - 1) * gap) / n);
        int total = n * cardW + (n - 1) * gap;
        int cx0 = x + (w - total) / 2, cy = y + 32;
        for (int i = 0; i < a.choices().size(); i++) {
            MarketPage.AmountChoice c = a.choices().get(i);
            int cx = cx0 + i * (cardW + gap);
            String key = "amount:" + menu.containerId + ":" + c.slot();
            boolean needConfirm = a.orderType() == null;
            boolean armed = needConfirm && isArmed(key);
            boolean hover = mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH;
            g.fill(cx, cy, cx + cardW, cy + cardH, armed ? C_BTN_ARMED : hover ? C_HOVER : C_CARD);
            ItemStack st = stack(menu, c.slot());
            g.renderItem(st, cx + (cardW - 16) / 2, cy + 6);
            g.renderItemDecorations(font, st, cx + (cardW - 16) / 2, cy + 6);
            String label = armed ? "Sicher?" : c.label();
            g.drawCenteredString(font, font.plainSubstrByWidth(label, cardW - 6), cx + cardW / 2, cy + 28, C_TEXT);
            g.drawCenteredString(font, font.plainSubstrByWidth(c.hint(), cardW - 6), cx + cardW / 2, cy + 41, C_MUTED);
            int slot = c.slot();
            targets.add(new Target(cx, cy, cardW, cardH, c.label(),
                    needConfirm ? () -> confirmThenClick(key, menu, slot, 0) : () -> click(menu, slot, 0)));
            if (hover) hovered = st;
        }
        int by = cy + cardH + 12;
        int bw = 150;
        if (a.custom() != null) {
            button(g, font, x + (w / 2) - bw - 4, by, bw, 18, "Eigene Menge eingeben…", true, false, mx, my,
                    () -> click(menu, a.custom().slot(), 0));
        }
        if (a.back() != null) {
            button(g, font, x + (w / 2) + 4, by, bw, 18, "◀ Zurück", true, false, mx, my,
                    () -> click(menu, a.back().slot(), 0));
        }
        if (a.custom() != null) {
            g.drawCenteredString(font, "Eigene Menge öffnet das Eingabefeld des Servers.", x + w / 2, by + 26, C_FAINT);
        }
        return hovered;
    }

    private static ItemStack renderPrice(AbstractContainerScreen<?> screen, GuiGraphics g, Font font,
                                         MarketPage.Price p, int x, int y, int w, int h, int mx, int my) {
        AbstractContainerMenu menu = screen.getMenu();
        String cur = cur();
        g.drawString(font, flowTitle(p.orderType(), 2, "Preis"), x + 8, y + 2, C_TEXT);
        Long amount = p.choices().isEmpty() ? null : p.choices().get(0).amount();
        if (amount != null) {
            g.drawString(font, "Menge: " + PriceFormat.number(amount) + (amount == 1 ? " Item" : " Items"), x + 8, y + 14, C_MUTED);
        }
        int n = Math.max(1, p.choices().size());
        int gap = 6, cardH = 48;
        int cardW = Math.min(180, (w - 16 - (n - 1) * gap) / n);
        int total = n * cardW + (n - 1) * gap;
        int cx0 = x + (w - total) / 2, cy = y + 30;
        for (int i = 0; i < p.choices().size(); i++) {
            MarketPage.PriceChoice c = p.choices().get(i);
            int cx = cx0 + i * (cardW + gap);
            boolean hover = mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH;
            g.fill(cx, cy, cx + cardW, cy + cardH, hover ? C_HOVER : C_CARD);
            g.drawCenteredString(font, font.plainSubstrByWidth(c.label(), cardW - 6), cx + cardW / 2, cy + 5, C_TITLE);
            String price = c.price() == null ? "–" : PriceFormat.exact(c.price(), cur) + " /Stk.";
            g.drawCenteredString(font, price, cx + cardW / 2, cy + 20, C_BUY);
            String tot = c.total() == null ? "" : "Gesamt " + PriceFormat.exact(c.total(), cur);
            g.drawCenteredString(font, font.plainSubstrByWidth(tot, cardW - 6), cx + cardW / 2, cy + 32, C_TEXT);
            int slot = c.slot();
            targets.add(new Target(cx, cy, cardW, cardH, c.label(), () -> click(menu, slot, 0)));
        }
        // Marktlage (Niedrigster Verkaufspreis / Höchster Kaufpreis / Spanne) einmal unter den Karten
        java.util.LinkedHashSet<String> extras = new java.util.LinkedHashSet<>();
        for (MarketPage.PriceChoice c : p.choices()) extras.addAll(c.extra());
        int ly = cy + cardH + 8;
        for (String e : extras) {
            g.drawCenteredString(font, font.plainSubstrByWidth(e, w - 16), x + w / 2, ly, C_MUTED);
            ly += 11;
        }
        int by = ly + 6;
        int bw = 150;
        if (p.custom() != null) {
            button(g, font, x + (w / 2) - bw - 4, by, bw, 18, "Eigenen Preis eingeben…", true, false, mx, my,
                    () -> click(menu, p.custom().slot(), 0));
        }
        if (p.back() != null) {
            button(g, font, x + (w / 2) + 4, by, bw, 18, "◀ Zurück", true, false, mx, my,
                    () -> click(menu, p.back().slot(), 0));
        }
        g.drawCenteredString(font, "Im nächsten Schritt bestätigst du den Auftrag.", x + w / 2, by + 26, C_FAINT);
        return null;
    }

    private static ItemStack renderConfirm(AbstractContainerScreen<?> screen, GuiGraphics g, Font font,
                                           MarketPage.Confirm c, int x, int y, int w, int h, int mx, int my) {
        AbstractContainerMenu menu = screen.getMenu();
        ItemStack hovered = null;
        String title = c.orderType() == null ? c.name() : c.orderType() + " erstellen · Schritt 3/3: Bestätigen";
        g.drawString(font, title, x + 8, y + 2, C_TEXT);

        int bw = Math.min(320, w - 16);
        int bx = x + (w - bw) / 2, by = y + 18;
        int boxH = 30 + Math.max(1, c.details().size()) * 11;
        g.fill(bx, by, bx + bw, by + boxH, C_SECTION);
        ItemStack st = stack(menu, c.slot());
        g.pose().pushMatrix();
        g.pose().translate(bx + 8, by + 7);
        g.pose().scale(2f, 2f);
        g.renderItem(st, 0, 0);
        g.pose().popMatrix();
        if (mx >= bx + 8 && mx < bx + 40 && my >= by + 7 && my < by + 39) hovered = st;
        g.drawString(font, c.name(), bx + 48, by + 7, C_TITLE);
        int ly = by + 20;
        for (String d : c.details()) {
            int col = d.startsWith("Gesamt") ? C_BUY : C_TEXT;
            g.drawString(font, font.plainSubstrByWidth(d, bw - 56), bx + 48, ly, col);
            ly += 11;
        }

        int btnY = by + boxH + 10;
        String hint = c.serverHint() == null ? "" : c.serverHint();
        String confirmLabel = c.orderType() == null ? c.name() : "Bestätigen";
        button(g, font, bx, btnY, bw, 22, confirmLabel, true, false, mx, my,
                () -> click(menu, c.slot(), 0));
        g.drawCenteredString(font, "Server: " + hint, x + w / 2, btnY + 28, C_SELL);
        g.drawCenteredString(font, "Jeder Klick auf „" + confirmLabel + "“ zählt beim Server als ein Klick.", x + w / 2, btnY + 40, C_FAINT);
        if (c.back() != null) {
            button(g, font, x + (w - 150) / 2, btnY + 56, 150, 18, c.orderType() == null ? "◀ Zurück" : "◀ Zurück / Abbrechen", true, false, mx, my,
                    () -> click(menu, c.back().slot(), 0));
        }
        return hovered;
    }

    // ---------------- Deine Aufträge ----------------

    private static ItemStack renderOrders(AbstractContainerScreen<?> screen, GuiGraphics g, Font font,
                                          MarketPage.Orders o, int x, int y, int w, int h, int mx, int my) {
        AbstractContainerMenu menu = screen.getMenu();
        String cur = cur();
        ItemStack hovered = null;
        g.drawString(font, "Deine Aufträge (" + o.orders().size() + ")", x + 8, y + 2, C_TEXT);

        // Filter: ein Klick = nächster Filter (wie im Original)
        if (o.filterButton() != null) {
            String label = "Filter: " + (o.filter() == null ? "?" : o.filter()) + "  ▶";
            int fw = Math.max(110, font.width(label) + 14);
            button(g, font, x + w - 8 - fw, y - 2, fw, 14, label, true, false, mx, my,
                    () -> click(menu, o.filterButton().slot(), 0));
            if (!o.filterOptions().isEmpty()) {
                String opts = String.join(" · ", o.filterOptions());
                g.drawString(font, opts, x + w - 8 - fw - 8 - font.width(opts), y + 1, C_FAINT);
            }
        }

        int ly = y + 16, lh = h - 16 - 26;
        int rowH = 30;
        int content = o.orders().size() * (rowH + 3);
        int maxScroll = Math.max(0, content - lh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        lastListArea = new int[]{x + 6, ly, w - 12, lh, maxScroll};
        int rx = x + 6, rw = w - 12 - (maxScroll > 0 ? 4 : 0);

        g.enableScissor(x + 6, ly, x + w - 6, ly + lh);
        for (int i = 0; i < o.orders().size(); i++) {
            MarketPage.Order od = o.orders().get(i);
            int ry = ly + i * (rowH + 3) - scroll;
            if (ry + rowH < ly || ry > ly + lh) continue;
            boolean inside = my >= ly && my < ly + lh;
            boolean hover = inside && mx >= rx && mx < rx + rw && my >= ry && my < ry + rowH;
            g.fill(rx, ry, rx + rw, ry + rowH, hover ? C_HOVER : C_CARD);
            ItemStack st = stack(menu, od.slot());
            g.renderItem(st, rx + 5, ry + 7);
            if (hover && mx < rx + 26) hovered = st;

            if (od.collectible()) g.fill(rx, ry, rx + 2, ry + rowH, C_BUY);
            boolean buy = !"Verkaufsangebot".equals(od.type());
            int typeCol = buy ? C_BUY : C_SELL;
            int c1 = rx + rw * 30 / 100, c2 = rx + rw * 44 / 100, c3 = rx + rw * 58 / 100;
            g.drawString(font, font.plainSubstrByWidth(od.name(), c1 - rx - 30), rx + 26, ry + 4, C_TEXT);
            g.drawString(font, od.type() == null ? "" : od.type(), rx + 26, ry + 16, typeCol);

            // Spalten: Menge, Preis/Stk., Gesamtwert, Fortschritt, Status
            g.drawString(font, "Menge", c1, ry + 4, C_FAINT);
            g.drawString(font, od.amount() == null ? "–" : PriceFormat.number(od.amount()) + "x", c1, ry + 16, C_TEXT);
            g.drawString(font, "Preis/Stk.", c2, ry + 4, C_FAINT);
            g.drawString(font, priceOrDash(od.price(), cur), c2, ry + 16, typeCol);
            g.drawString(font, "Gesamt", c3, ry + 4, C_FAINT);
            g.drawString(font, priceOrDash(od.total(), cur), c3, ry + 16, C_TEXT);

            int px = rx + rw * 73 / 100, pw = rx + rw - 6 - px;
            if (pw > 40) {
                String status = od.status() == null ? "" : od.status();
                g.drawString(font, font.plainSubstrByWidth(status, pw), px + pw - font.width(font.plainSubstrByWidth(status, pw)), ry + 4, C_TITLE);
                String done = od.filled() == null ? "" : PriceFormat.number(od.filled()) + "/" + PriceFormat.number(od.filledOf());
                g.drawString(font, done, px, ry + 4, C_MUTED);
                double pct = od.percent() != null ? od.percent()
                        : od.filled() != null && od.filledOf() != null && od.filledOf() > 0 ? 100.0 * od.filled() / od.filledOf() : 0;
                int bw = pw;
                g.fill(px, ry + 17, px + bw, ry + 23, 0x40FFFFFF);
                g.fill(px, ry + 17, px + (int) Math.round(bw * Math.max(0, Math.min(100, pct)) / 100.0), ry + 23, typeCol);
            }
            int top = Math.max(ry, ly), bottom = Math.min(ry + rowH, ly + lh);
            if (bottom > top) {
                int slot = od.slot();
                targets.add(new Target(rx, top, rw, bottom - top, "Auftrag " + od.name(), () -> click(menu, slot, 0)));
            }
            if (hover && !od.other().isEmpty() && hovered == null) {
                List<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
                for (String l : od.other()) tip.add(Component.literal(l).getVisualOrderText());
                g.setTooltipForNextFrame(font, tip, mx, my);
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, lh * lh / content);
            int barY = ly + (int) ((lh - barH) * (scroll / (double) maxScroll));
            g.fill(x + w - 8, ly, x + w - 6, ly + lh, 0x30FFFFFF);
            g.fill(x + w - 8, barY, x + w - 6, barY + barH, C_BORDER);
        }
        if (o.orders().isEmpty()) {
            g.drawCenteredString(font, "Keine Aufträge" + (o.filter() == null ? "." : " im Filter „" + o.filter() + "“."),
                    x + w / 2, ly + 30, C_MUTED);
        }

        // Fußzeile
        int fy = y + h - 20;
        int bx = x + 6;
        if (o.back() != null) {
            button(g, font, bx, fy, 90, 16, "◀ Zurück", true, false, mx, my, () -> click(menu, o.back().slot(), 0));
            bx += 94;
        }
        for (MarketPage.Button b : o.extra()) {
            int bw = Math.max(70, font.width(b.label()) + 12);
            button(g, font, bx, fy, bw, 16, b.label(), true, false, mx, my, () -> click(menu, b.slot(), 0));
            bx += bw + 4;
        }
        int collectible = 0;
        for (MarketPage.Order od : o.orders()) if (od.collectible()) collectible++;
        {
            String label = collecting ? "Stopp (" + collected + " eingesammelt)" : "Alle einsammeln (" + collectible + ")";
            int cw = Math.max(120, font.width(label) + 14);
            int n = collectible;
            boolean enabled = collecting || n > 0;
            // Während des Einsammelns stoppt jeder Klick (siehe mouseClicked), daher keine eigene Aktion nötig
            button(g, font, bx, fy, cw, 16, label, enabled, collecting, mx, my,
                    collecting ? () -> stopCollect("abgebrochen") : () -> startCollect(menu, n));
            if (!enabled && mx >= bx && mx < bx + cw && my >= fy && my < fy + 16) {
                g.setTooltipForNextFrame(font, List.of(
                        Component.literal("Nichts zum Einsammeln auf dieser Seite.").getVisualOrderText(),
                        Component.literal("Tipp: Filter auf „Einsammelbar“ stellen.").withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                        mx, my);
            }
            bx += cw + 4;
        }
        String hint = "Klick auf Auftrag: verwalten";
        g.drawString(font, font.plainSubstrByWidth(hint, x + w - 8 - bx - 4), x + w - 8 - Math.min(font.width(hint), x + w - 8 - bx - 4), fy + 4, C_FAINT);
        return hovered;
    }

    private static void orderTable(GuiGraphics g, Font font, int x, int y, int w, int h, String title,
                                   List<MarketPage.OrderLevel> levels, int color, String cur) {
        g.fill(x, y, x + w, y + h, C_SECTION);
        g.drawString(font, title, x + 6, y + 5, C_TITLE);
        int ly = y + 18;
        g.drawString(font, "Preis", x + 6, ly, C_FAINT);
        String m = "Menge", a = "Anz.";
        g.drawString(font, m, x + w - 50 - font.width(m), ly, C_FAINT);
        g.drawString(font, a, x + w - 6 - font.width(a), ly, C_FAINT);
        ly += 11;
        if (levels.isEmpty()) {
            g.drawString(font, "Keine", x + 6, ly, C_FAINT);
            return;
        }
        for (MarketPage.OrderLevel l : levels) {
            if (ly + 9 > y + h) break;
            g.drawString(font, PriceFormat.exact(l.price(), cur), x + 6, ly, color);
            String amount = PriceFormat.number(l.amount()) + "x";
            g.drawString(font, amount, x + w - 50 - font.width(amount), ly, C_TEXT);
            String n = String.valueOf(l.orders());
            g.drawString(font, n, x + w - 6 - font.width(n), ly, C_MUTED);
            ly += 10;
        }
    }

    /** Kleiner Knopf im Original-Menü, um zum Overlay zurückzukehren. */
    public static void renderOriginalToggle(AbstractContainerScreen<?> screen, GuiGraphics g, int mx, int my) {
        if (!showOriginal || config == null || !config.marketOverlay) return;
        if (!SlotInfo.strip(screen.getTitle().getString()).contains("Marktplatz")) return;
        targets.clear();
        button(g, Minecraft.getInstance().font, 6, 6, 90, 16, "Overlay zeigen", true, false, mx, my,
                () -> showOriginal = false);
    }

    // ---------------- Eingabe ----------------

    /** Liefert true, wenn der Klick vom Overlay verarbeitet (und damit vom Original-Menü ferngehalten) wurde. */
    public static boolean mouseClicked(AbstractContainerScreen<?> screen, double mx, double my, int button) {
        if (showOriginal) {
            for (Target t : List.copyOf(targets)) {
                if (t.hit(mx, my)) {
                    t.action().run();
                    return true;
                }
            }
            return false;
        }
        if (!isActive(screen)) return false;
        if (collecting) {
            stopCollect("abgebrochen");
            return true;
        }
        if (button == 0) {
            for (Target t : List.copyOf(targets)) {
                if (t.hit(mx, my)) {
                    t.action().run();
                    break;
                }
            }
        }
        return true; // Klicks gehen nie an die verdeckten Original-Slots
    }

    /** Mausrad: scrollt die Item-Liste. Liefert true, wenn das Overlay aktiv ist. */
    public static boolean mouseScrolled(AbstractContainerScreen<?> screen, double mx, double my, double amount) {
        if (!isActive(screen)) return false;
        int[] a = lastListArea;
        if (a != null && mx >= a[0] && mx < a[0] + a[2] && my >= a[1] && my < a[1] + a[3]) {
            scroll = (int) Math.max(0, Math.min(a[4], scroll - amount * 20));
        }
        return true;
    }

    private static boolean isArmed(String key) {
        return key.equals(armedKey) && System.currentTimeMillis() < armedUntil;
    }

    /** Erster Klick schaltet scharf (3 s), zweiter Klick sendet. */
    private static void confirmThenClick(String key, AbstractContainerMenu menu, int slot, int button) {
        if (isArmed(key)) {
            armedKey = null;
            click(menu, slot, button);
        } else {
            armedKey = key;
            armedUntil = System.currentTimeMillis() + 3000;
        }
    }

    /** Genau ein Klick auf einen Slot des Server-Menüs (mit Doppelklick-Schutz). */
    private static void click(AbstractContainerMenu menu, int slot, int button) {
        long now = System.currentTimeMillis();
        if (now - lastClickMs < 200) return; // versehentliche Doppelklicks abfangen
        lastClickMs = now;
        if (button != 0) armedKey = null;
        sendClick(menu, slot, button);
    }

    /** Sendet den Klick an den Server, wenn das Menü noch offen ist. */
    private static boolean sendClick(AbstractContainerMenu menu, int slot, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || mc.player.containerMenu != menu) return false;
        if (slot < 0 || slot >= menu.slots.size()) return false;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, ClickType.PICKUP, mc.player);
        return true;
    }

    /** Für automatische Tests: Mitte der Klickfläche, deren Beschriftung mit dem Text beginnt (GUI-Koordinaten). */
    public static int[] findTarget(String labelPrefix) {
        for (Target t : List.copyOf(targets)) {
            if (t.label() != null && t.label().startsWith(labelPrefix)) return new int[]{t.x() + t.w() / 2, t.y() + t.h() / 2};
        }
        return null;
    }

    // ---------------- Hilfen ----------------

    private static ItemStack stack(AbstractContainerMenu menu, int slot) {
        return slot >= 0 && slot < menu.slots.size() ? menu.slots.get(slot).getItem() : ItemStack.EMPTY;
    }

    private static String cur() {
        return config == null ? "$" : config.currencyLabel;
    }

    private static String priceOrDash(Double p, String cur) {
        return p == null ? "–" : PriceFormat.exact(p, cur);
    }

    private static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean enabled,
                               boolean armed, int mx, int my, Runnable action) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = !enabled ? C_BTN_OFF : armed ? C_BTN_ARMED : hover ? C_BTN_HOVER : C_BTN;
        g.fill(x, y, x + w, y + h, hover || armed ? C_BORDER : 0xFF3A3A48);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        String s = font.plainSubstrByWidth(label, w - 6);
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, enabled ? C_TEXT : C_FAINT);
        if (enabled) targets.add(new Target(x, y, w, h, label, action));
    }

    private static boolean iconButton(GuiGraphics g, Font font, int x, int y, int w, int h, ItemStack icon, String label,
                                      int mx, int my, Runnable action) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hover ? C_HOVER : C_CARD);
        g.renderItem(icon, x + 2, y + 2);
        g.drawString(font, font.plainSubstrByWidth(label, w - 24), x + 22, y + 6, C_TEXT);
        targets.add(new Target(x, y, w, h, label, action));
        return hover;
    }
}
