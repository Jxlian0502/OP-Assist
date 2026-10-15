package dev.opsuchtprices.client;

import dev.opsuchtprices.cache.SaleTracker;
import dev.opsuchtprices.cache.SalesStore;
import dev.opsuchtprices.config.ModConfig;
import dev.opsuchtprices.model.SaleRecord;
import dev.opsuchtprices.util.PriceFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Übersicht aller lokal gespeicherten AH-Verkäufe mit Item-Bildern,
 * Suche, Sortierung und Details beim Drüberfahren.
 */
public final class SalesScreen extends Screen {

    private enum Sort {
        NEWEST("Neueste"), PRICE("Teuerste"), COUNT("Meiste Verkäufe"), NAME("Name A-Z");
        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    /** Eine Zeile der Liste mit vorbereiteten Anzeigedaten. */
    private record Row(SalesStore.ItemOverview item, String name, List<Component> enchants, ItemStack icon) {}

    private static final int ROW_HEIGHT = 24;
    private static final int PANEL_MAX_WIDTH = 380;

    // Farben (ARGB, Alpha muss gesetzt sein)
    private static final int C_PANEL = 0xE0101018;
    private static final int C_BORDER = 0xFF8A6A1F;
    private static final int C_ROW_A = 0x40FFFFFF;
    private static final int C_ROW_B = 0x20FFFFFF;
    private static final int C_ROW_HOVER = 0x60FFD24A;
    private static final int C_TITLE = 0xFFFFC94A;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFFA8A8A8;
    private static final int C_PRICE = 0xFFFFD24A;
    private static final int C_SCROLL = 0xFFB08A2E;

    private final SaleTracker tracker;
    private final ModConfig config;
    private final RemoteIcons remoteIcons;
    private final String initialFilter;
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    private EditBox search;
    private Button sortButton;
    private Sort sort = Sort.NEWEST;
    private List<Row> rows = List.of();
    private int totalItems;
    private double scroll;

    private int panelX, panelY, panelW, panelH, listY, listH;

    public SalesScreen(SaleTracker tracker, ModConfig config, RemoteIcons remoteIcons, String filter) {
        super(Component.literal("OP SUCHT AH-Verkäufe"));
        this.tracker = tracker;
        this.config = config;
        this.remoteIcons = remoteIcons;
        this.initialFilter = filter == null ? "" : filter;
    }

    @Override
    protected void init() {
        tracker.requestIconBackfill(); // Bilder für ältere Einträge nachladen (im Hintergrund)
        panelW = Math.min(PANEL_MAX_WIDTH, this.width - 20);
        panelH = this.height - 30;
        panelX = (this.width - panelW) / 2;
        panelY = 15;

        int searchW = panelW - 16 - 110 - 4;
        String previous = search == null ? initialFilter : search.getValue();
        search = new EditBox(this.font, panelX + 8, panelY + 22, searchW, 16, Component.literal("Suche"));
        search.setMaxLength(64);
        search.setHint(Component.literal("Item suchen…").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(previous);
        search.setResponder(text -> {
            scroll = 0;
            rebuildRows();
        });
        addRenderableWidget(search);

        sortButton = Button.builder(Component.literal("Sortierung: " + sort.label), b -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            b.setMessage(Component.literal("Sortierung: " + sort.label));
            scroll = 0;
            rebuildRows();
        }).bounds(panelX + panelW - 8 - 110, panelY + 20, 110, 20).build();
        addRenderableWidget(sortButton);

        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
                .bounds(panelX + panelW / 2 - 50, panelY + panelH - 26, 100, 20).build());

        listY = panelY + 46;
        listH = panelH - 46 - 32;
        rebuildRows();
    }

    private void rebuildRows() {
        List<SalesStore.ItemOverview> all = tracker.sales().overview();
        totalItems = all.size();
        String f = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>();
        for (SalesStore.ItemOverview o : all) {
            Row r = toRow(o);
            if (!f.isEmpty() && !r.name().toLowerCase(Locale.ROOT).contains(f)
                    && !o.label().toLowerCase(Locale.ROOT).contains(f)) continue;
            out.add(r);
        }
        Comparator<Row> cmp = switch (sort) {
            case NEWEST -> Comparator.comparing((Row r) -> r.item().summary().last().soldAt()).reversed();
            case PRICE -> Comparator.comparingDouble((Row r) -> r.item().summary().avgPerItem()).reversed();
            case COUNT -> Comparator.comparingInt((Row r) -> r.item().summary().count()).reversed();
            case NAME -> Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT));
        };
        out.sort(cmp);
        rows = out;
        clampScroll();
    }

    /** Zerlegt den Schlüssel "MATERIAL|name|ench" in Anzeigedaten. */
    private Row toRow(SalesStore.ItemOverview o) {
        String[] parts = o.key().split("\\|", -1);
        String material = parts.length > 0 ? parts[0] : "";
        String keyName = parts.length > 1 ? parts[1] : "";
        String ench = parts.length > 2 ? parts[2] : "";
        ItemStack icon = iconCache.computeIfAbsent(o.key(), k -> iconFor(material, !ench.isEmpty()));

        String name;
        String label = o.summary().last().label();
        if (!keyName.isEmpty()) {
            // Originalschreibweise aus dem gespeicherten Label, sonst der normalisierte Name
            int cut = label == null ? -1 : label.lastIndexOf(" (");
            name = cut > 0 ? label.substring(0, cut) : keyName;
        } else {
            name = icon.is(Items.BARRIER) ? material : icon.getHoverName().getString();
        }
        return new Row(o, name, enchantComponents(ench), icon);
    }

    private static ItemStack iconFor(String material, boolean enchanted) {
        Identifier id = Identifier.tryParse("minecraft:" + material.toLowerCase(Locale.ROOT));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        ItemStack stack = new ItemStack(item == Items.AIR ? Items.BARRIER : item);
        if (enchanted) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** "minecraft:sharpness=5,minecraft:unbreaking=3" -> übersetzte Namen mit Stufe. */
    private static List<Component> enchantComponents(String ench) {
        List<Component> out = new ArrayList<>();
        if (ench.isEmpty()) return out;
        for (String part : ench.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String id = part.substring(0, eq);
            String level = part.substring(eq + 1);
            int colon = id.indexOf(':');
            String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
            String path = colon < 0 ? id : id.substring(colon + 1);
            MutableComponent c = Component.translatableWithFallback("enchantment." + ns + "." + path, path);
            c.append(" ").append(Component.translatableWithFallback("enchantment.level." + level, level));
            out.add(c.withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    // ---------------- Darstellung ----------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Panel
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, C_BORDER);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);

        String title = "Gespeicherte AH-Verkäufe";
        g.drawString(this.font, title, panelX + 8, panelY + 7, C_TITLE);
        String count = rows.size() == totalItems ? totalItems + " Items"
                : rows.size() + " von " + totalItems + " Items";
        g.drawString(this.font, count, panelX + panelW - 8 - this.font.width(count), panelY + 7, C_MUTED);

        super.render(g, mouseX, mouseY, delta); // Suchfeld und Buttons

        Instant now = tracker.clock().instant();
        String cur = config.currencyLabel;
        Row hovered = null;

        if (rows.isEmpty()) {
            String msg = totalItems == 0 ? "Noch keine Verkäufe gespeichert." : "Keine Treffer.";
            g.drawCenteredString(this.font, msg, panelX + panelW / 2, listY + 20, C_MUTED);
        } else {
            int listRight = panelX + panelW - 12;
            g.enableScissor(panelX + 4, listY, listRight, listY + listH);
            int first = (int) (scroll / ROW_HEIGHT);
            for (int i = first; i < rows.size(); i++) {
                int y = listY + i * ROW_HEIGHT - (int) scroll;
                if (y > listY + listH) break;
                Row r = rows.get(i);
                boolean hover = mouseX >= panelX + 4 && mouseX < listRight && mouseY >= Math.max(y, listY)
                        && mouseY < Math.min(y + ROW_HEIGHT, listY + listH);
                if (hover) hovered = r;
                g.fill(panelX + 4, y, listRight, y + ROW_HEIGHT - 1,
                        hover ? C_ROW_HOVER : (i % 2 == 0 ? C_ROW_A : C_ROW_B));
                drawIcon(g, r, panelX + 8, y + 4);

                SalesStore.Summary s = r.item().summary();
                int textX = panelX + 30;
                int textW = listRight - textX - 4;

                String price = "Schnitt " + PriceFormat.price(s.avgPerItem(), cur);
                int priceW = this.font.width(price);
                g.drawString(this.font, this.font.plainSubstrByWidth(r.name(), textW - priceW - 8),
                        textX, y + 3, C_TEXT);
                g.drawString(this.font, price, listRight - 4 - priceW, y + 3, C_PRICE);

                String line2 = "Letzter: " + PriceFormat.price(s.last().pricePerItem(), cur) + " · "
                        + PriceFormat.ago(s.last().soldAt(), now) + " · " + s.count()
                        + (s.count() == 1 ? " Verkauf" : " Verkäufe");
                g.drawString(this.font, this.font.plainSubstrByWidth(line2, textW), textX, y + 13, C_MUTED);
            }
            g.disableScissor();

            // Scrollbalken
            int content = rows.size() * ROW_HEIGHT;
            if (content > listH) {
                int barH = Math.max(16, listH * listH / content);
                int barY = listY + (int) ((listH - barH) * (scroll / (content - listH)));
                g.fill(listRight + 3, listY, listRight + 6, listY + listH, 0x40FFFFFF);
                g.fill(listRight + 3, barY, listRight + 6, barY + barH, C_SCROLL);
            }
        }

        if (hovered != null) {
            g.setComponentTooltipForNextFrame(this.font, tooltipFor(hovered, now, cur), mouseX, mouseY);
        }
    }

    /** Custom-Textur von OP SUCHT, sonst das normale Minecraft-Item. */
    private void drawIcon(GuiGraphics g, Row r, int x, int y) {
        if (config.showCustomIcons && remoteIcons != null) {
            String url = tracker.sales().iconFor(r.item().key());
            Identifier tex = url == null ? null : remoteIcons.get(url);
            if (tex != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, 16, 16, 16, 16);
                return;
            }
        }
        g.renderItem(r.icon(), x, y);
    }

    private List<Component> tooltipFor(Row r, Instant now, String cur) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(r.name()).withStyle(ChatFormatting.GOLD));
        lines.addAll(r.enchants());
        SalesStore.Summary s = r.item().summary();
        lines.add(Component.empty());
        lines.add(Component.literal("Durchschnitt: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(PriceFormat.price(s.avgPerItem(), cur) + " /Stk.").withStyle(ChatFormatting.YELLOW)));
        lines.add(Component.literal("Gespeicherte Verkäufe: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(s.count())).withStyle(ChatFormatting.YELLOW)));
        lines.add(Component.empty());
        lines.add(Component.literal("Letzte Verkäufe:").withStyle(ChatFormatting.GRAY));
        for (SaleRecord rec : tracker.sales().recent(r.item().key(), 5)) {
            String amount = rec.amount() > 1
                    ? " (" + rec.amount() + "× für " + PriceFormat.price(rec.totalPrice(), cur) + ")" : "";
            String how = "INSTANT_BUY".equals(rec.type()) ? "Sofortkauf" : "Versteigert";
            lines.add(Component.literal(" " + PriceFormat.price(rec.pricePerItem(), cur) + amount)
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" · " + how + " · " + PriceFormat.ago(rec.soldAt(), now))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        return lines;
    }

    // ---------------- Eingabe ----------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= listY && mouseY < listY + listH) {
            scroll -= scrollY * ROW_HEIGHT;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        double max = Math.max(0, rows.size() * ROW_HEIGHT - listH);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
