package dev.opsuchtprices.client;

import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.job.JobTracker;
import dev.opsuchtprices.util.PriceFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** HUD des Jobtrackers: Job, Level, Fortschritt, Zeit, Geld, XP, pro Stunde, Zeit bis Level-Up. */
public final class JobHud {
    private JobHud() {}

    private static JobTracker tracker;
    private static ModConfig config;
    private static BooleanSupplier active = () -> true;
    private static dev.opsuchtprices.job.XpCounter counter;
    private static final int C_COUNTER = 0xFF6AB8FF;

    private static final Map<String, Item> ICONS = Map.of(
            "minenarbeiter", Items.DIAMOND_PICKAXE,
            "holzfäller", Items.IRON_AXE,
            "gräber", Items.IRON_SHOVEL,
            "jäger", Items.IRON_SWORD,
            "fischer", Items.FISHING_ROD,
            "farmer", Items.WHEAT,
            "builder", Items.BRICKS);

    private static final int C_BG = 0x90000000;
    private static final int C_TITLE = 0xFFFFC94A;
    private static final int C_LABEL = 0xFFA8A8A8;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MONEY = 0xFF7CDB6A;
    private static final int C_XP = 0xFFE6D45A;
    private static final int C_PAUSE = 0xFFFF7A5A;
    private static final int C_SHARD = 0xFFC77DFF;
    private static final int C_TREES = 0xFF8FD16A;
    private static final int C_REDCOIN = 0xFFFF5A5A;

    public static void init(JobTracker jobTracker, ModConfig modConfig, BooleanSupplier isActive) {
        tracker = jobTracker;
        config = modConfig;
        active = isActive;
    }

    public static void setCounter(dev.opsuchtprices.job.XpCounter xpCounter) {
        counter = xpCounter;
    }

    /**
     * Von GuiMixin für jede Actionbar aufgerufen. Liefert true, wenn die Actionbar ausgeblendet werden soll.
     */
    public static boolean onActionBar(net.minecraft.network.chat.Component message) {
        if (tracker == null || message == null) return false;
        if (config != null && !config.jobTracker) return false;
        boolean isJob;
        try {
            isJob = tracker.onActionBar(message.getString());
            JobTracker.Stats cur = isJob ? tracker.current() : null;
            if (cur != null && cur.job.toLowerCase(java.util.Locale.ROOT).startsWith("holzf")
                    && TREES.onHolzfaellerBar(System.currentTimeMillis())) {
                StatsTracker.onTree();
            }
        } catch (RuntimeException e) {
            dev.opsuchtprices.util.ModLog.warn("Job-Actionbar nicht lesbar: {}", e.toString());
            return false;
        }
        return isJob && config != null && config.jobHideActionBar;
    }

    /** Beispieldaten für die Positionierung, wenn noch kein Job läuft. */
    public static JobTracker previewTracker() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1_000_000);
        JobTracker t = new JobTracker(clock::get, 60_000);
        for (int i = 0; i < 120; i++) {
            clock.addAndGet(5000);
            t.onActionBar("+2,75 XP • +15,887$ • Holzfäller Level 78 • [] "
                    + String.format(java.util.Locale.GERMANY, "%.2f", 50 + i * 0.05) + "%");
        }
        return t;
    }

    /** Erkennung gefällter Bäume (Anschlagen eines Stamms + mehrere Holzfäller-Meldungen). */
    public static final dev.opsuchtprices.job.TreeCounter TREES = new dev.opsuchtprices.job.TreeCounter();

    public static ModConfig config() {
        return config;
    }

    public static JobTracker tracker() {
        return tracker;
    }

    /** HUD-Element (Fabric HudElementRegistry). */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (tracker == null || config == null || !config.jobTracker || mc.options.hideGui || !active.getAsBoolean()) return;
        if (hiddenByOverlay(mc, config)) return;
        JobTracker.Stats s = tracker.current();
        if (s == null) return;
        if (config.jobHideAfterMinutes > 0 && tracker.sinceLastEventMs(s) > config.jobHideAfterMinutes * 60_000L) return;
        // Position aus dem Drag-&-Drop-Menü, auf den Bildschirm begrenzt
        int x = Math.max(0, Math.min(config.jobHudX, g.guiWidth() - lastSize[0]));
        int y = Math.max(0, Math.min(config.jobHudY, g.guiHeight() - lastSize[1]));
        int[] size = renderPanel(g, x, y, tracker);
        if (size != null) lastSize = size;
    }

    /**
     * true, wenn das HUD gerade nicht gezeichnet werden soll: Tabliste offen (Tab gedrückt) oder
     * ein Menü offen (Markt-Overlay, Inventar, Truhe …). Im Chat bleibt das HUD sichtbar.
     */
    static boolean hiddenByOverlay(Minecraft mc, ModConfig cfg) {
        return hiddenByOverlay(mc.screen, mc.options.keyPlayerList.isDown(), cfg);
    }

    /** Entscheidung ohne Minecraft-Zustand (auch für Tests). */
    public static boolean hiddenByOverlay(net.minecraft.client.gui.screens.Screen screen, boolean tabListDown, ModConfig cfg) {
        if (cfg.jobHideWithTabList && tabListDown) return true;
        return cfg.jobHideInMenus && screen != null
                && !(screen instanceof net.minecraft.client.gui.screens.ChatScreen);
    }

    private static int[] lastSize = {120, 100};
    private static volatile int lastPanelH;

    /** Höhe des zuletzt gezeichneten Panels (für Tests). */
    public static int lastPanelHeight() {
        return lastPanelH;
    }

    /** Zeichnet das Panel an x/y (auch für Tests). */
    public static void renderPanel(GuiGraphics g, int x, int y) {
        renderPanel(g, x, y, tracker);
    }

    /** Zeichnet das Panel mit den Daten von t. Liefert Breite und Höhe (oder null, wenn nichts zu zeigen ist). */
    public static int[] renderPanel(GuiGraphics g, int x, int y, JobTracker t) {
        JobTracker.Stats s = t == null ? null : t.current();
        if (s == null) return null;
        Font font = Minecraft.getInstance().font;
        String cur = config == null ? "$" : config.currencyLabel;
        boolean paused = t.isPaused(s);

        String title = s.job + " · Level " + s.level;
        List<String[]> rows = new ArrayList<>();
        if (on(c -> c.jobShowTime)) rows.add(new String[]{"Zeit", duration(t.activeMs(s)) + (paused ? " (Pause)" : "")});
        if (on(c -> c.jobShowMoney)) rows.add(new String[]{"Geld", "+" + PriceFormat.exact(s.money, cur)});
        refreshRates(t, s);
        Double mph = cachedMph;
        if (on(c -> c.jobShowMoneyPerHour)) rows.add(new String[]{"Geld/h", mph == null ? "…" : PriceFormat.price(mph, cur)});
        if (on(c -> c.jobShowXp)) rows.add(new String[]{"XP", "+" + PriceFormat.exact(s.xp, "")});
        Double xph = cachedXph;
        if (on(c -> c.jobShowXpPerHour)) rows.add(new String[]{"XP/h", xph == null ? "…" : PriceFormat.number(xph)});
        Long lvl = t.timeToLevelUpMs(s);
        if (on(c -> c.jobShowLevelUp)) rows.add(new String[]{"Level-Up", lvl == null ? "…" : "~" + shortDuration(lvl)});
        if (config == null || config.jobShowShards) {
            if (ShardValue.ready()) {
                rows.add(new String[]{"Shards (Inv.)", PriceFormat.exact(ShardValue.total("opshards"), "")});
                double rc = ShardValue.total("redcoins");
                if (rc > 0) rows.add(new String[]{"Redcoins (Inv.)", PriceFormat.exact(rc, "")});
            } else {
                rows.add(new String[]{"Shards (Inv.)", ShardValue.error() == null ? "lädt…" : "API-Fehler"});
            }
        }

        int labelW = 0, valueW = 0;
        for (String[] r : rows) {
            labelW = Math.max(labelW, font.width(r[0]));
            valueW = Math.max(valueW, font.width(r[1]));
        }
        int w = Math.max(20 + font.width(title), labelW + 10 + valueW) + 8;
        w = Math.max(w, 120);
        int lineH = 10;
        boolean showCounter = counter != null && (config == null || config.jobXpCounterStep > 0) && on(c -> c.jobShowTreasure);
        boolean showBar = on(c -> c.jobShowProgress);
        String cLabel = null, cValue = null;
        if (showCounter) {
            cLabel = "Jobschatz";
            cValue = PriceFormat.number(Math.floor(counter.current())) + " / " + PriceFormat.number(counter.step());
            w = Math.max(w, font.width(cLabel) + 10 + font.width(cValue) + 8);
        }
        int h = 4 + 16 + (showBar ? 10 : 0) + rows.size() * lineH + 3 + (showCounter ? 26 : 0);

        g.fill(x, y, x + w, y + h, C_BG);
        Item icon = ICONS.getOrDefault(s.job.toLowerCase(Locale.ROOT), Items.EXPERIENCE_BOTTLE);
        g.renderItem(new ItemStack(icon), x + 3, y + 2);
        g.drawString(font, title, x + 22, y + 6, C_TITLE);

        // Fortschrittsbalken
        if (showBar) {
            int by = y + 20, bx = x + 4, bw = w - 8 - font.width("100,00 %") - 4;
            double pct = s.percent == null ? 0 : Math.max(0, Math.min(100, s.percent));
            g.fill(bx, by + 1, bx + bw, by + 6, 0x60FFFFFF);
            g.fill(bx, by + 1, bx + (int) Math.round(bw * pct / 100.0), by + 6, C_MONEY);
            String p = s.percent == null ? "–" : PriceFormat.exact(s.percent, "") + " %";
            g.drawString(font, p, x + w - 4 - font.width(p), by, C_TEXT);
        }

        int ly = y + (showBar ? 31 : 21);
        for (String[] r : rows) {
            int col = switch (r[0]) {
                case "Geld", "Geld/h" -> C_MONEY;
                case "XP", "XP/h" -> C_XP;
                case "Shards (Inv.)" -> C_SHARD;
                case "Redcoins (Inv.)" -> C_REDCOIN;
                case "Zeit" -> paused ? C_PAUSE : C_TEXT;
                default -> C_TEXT;
            };
            g.drawString(font, r[0], x + 4, ly, C_LABEL);
            g.drawString(font, r[1], x + w - 4 - font.width(r[1]), ly, col);
            ly += lineH;
        }
        if (showCounter) {
            ly += 3;
            g.fill(x + 4, ly - 2, x + w - 4, ly - 1, 0x40FFFFFF);
            g.drawString(font, cLabel, x + 4, ly + 1, C_LABEL);
            g.drawString(font, cValue, x + w - 4 - font.width(cValue), ly + 1, C_COUNTER);
            int cy = ly + 12, cw = w - 8 - font.width("999× gefunden") - 4;
            double frac = counter.step() <= 0 ? 0 : Math.max(0, Math.min(1, counter.current() / counter.step()));
            g.fill(x + 4, cy + 1, x + 4 + cw, cy + 6, 0x60FFFFFF);
            g.fill(x + 4, cy + 1, x + 4 + (int) Math.round(cw * frac), cy + 6, C_COUNTER);
            String r = counter.found() + "× gefunden";
            g.drawString(font, r, x + w - 4 - font.width(r), cy, C_TEXT);
        }

        lastPanelH = h;
        return new int[]{w, h};
    }

    // Geld/h und XP/h nur alle 2 Sekunden neu berechnen, damit die Zahlen nicht flimmern
    private static final long RATE_REFRESH_MS = 2000;
    private static long ratesAt;
    private static String ratesJob;
    private static Double cachedMph, cachedXph;

    private static JobTracker ratesTracker;

    private static void refreshRates(JobTracker t, JobTracker.Stats s) {
        long now = System.currentTimeMillis();
        if (now - ratesAt < RATE_REFRESH_MS && s.job.equals(ratesJob) && t == ratesTracker) return;
        ratesAt = now;
        ratesJob = s.job;
        ratesTracker = t;
        cachedMph = t.perHour(s, s.money);
        cachedXph = t.perHour(s, s.xp);
    }

    /** true, wenn die Zeile angezeigt werden soll (ohne Config, z. B. in Tests: alles an). */
    private static boolean on(java.util.function.Predicate<ModConfig> show) {
        return config == null || show.test(config);
    }

    /** 1:02:03 oder 12:34. */
    static String duration(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** "3 Std. 5 Min." / "12 Min." / "<1 Min." */
    static String shortDuration(long ms) {
        long m = ms / 60_000;
        if (m < 1) return "<1 Min.";
        if (m < 60) return m + " Min.";
        long h = m / 60;
        if (h < 48) return h + " Std. " + (m % 60) + " Min.";
        return (h / 24) + " Tage";
    }
}
