package dev.opsuchtprices.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Farbcodes wie auf OP SUCHT (&a, &l, …, und Hex &#RRGGBB) in einen farbigen Text umwandeln,
 * für die Vorschau beim Sign- und Rename-Schutz.
 */
public final class ColorCodes {
    private ColorCodes() {}

    public static MutableComponent toComponent(String text) {
        MutableComponent out = Component.empty();
        if (text == null) return out;
        Style style = Style.EMPTY.withItalic(false);
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                // Hex: &#RRGGBB
                if (text.charAt(i + 1) == '#' && i + 8 <= text.length() && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    flush(out, buf, style);
                    style = Style.EMPTY.withItalic(false).withColor(TextColor.fromRgb(Integer.parseInt(text.substring(i + 2, i + 8), 16)));
                    i += 7;
                    continue;
                }
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
                if (f != null) {
                    flush(out, buf, style);
                    if (f == ChatFormatting.RESET) style = Style.EMPTY.withItalic(false);
                    else if (f.isColor()) style = Style.EMPTY.withItalic(false).withColor(f);
                    else style = style.applyFormat(f);
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        flush(out, buf, style);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder buf, Style style) {
        if (buf.isEmpty()) return;
        out.append(Component.literal(buf.toString()).setStyle(style));
        buf.setLength(0);
    }
}
