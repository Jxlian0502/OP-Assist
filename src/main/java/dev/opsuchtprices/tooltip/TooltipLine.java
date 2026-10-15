package dev.opsuchtprices.tooltip;

/** Eine Tooltip-Zeile, unabhängig von Minecraft. */
public record TooltipLine(String label, String value, Kind kind) {

    public enum Kind { VALUE, MUTED }

    public static TooltipLine value(String label, String value) {
        return new TooltipLine(label, value, Kind.VALUE);
    }

    public static TooltipLine muted(String text) {
        return new TooltipLine(text, "", Kind.MUTED);
    }

    public String plain() {
        return value.isEmpty() ? label : label + ": " + value;
    }
}
