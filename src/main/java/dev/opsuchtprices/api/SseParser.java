package dev.opsuchtprices.api;

import java.util.function.Consumer;

/**
 * Zeilenbasierter Parser für Server-Sent Events (text/event-stream).
 * Unterstützt die Felder event, data (mehrzeilig), id und Kommentare.
 */
public final class SseParser {

    public record Event(String id, String event, String data) {}

    private final Consumer<Event> sink;
    private final StringBuilder data = new StringBuilder();
    private String eventName;
    private String eventId;
    private String lastEventId;

    public SseParser(Consumer<Event> sink) {
        this.sink = sink;
    }

    public String lastEventId() {
        return lastEventId;
    }

    public void accept(String line) {
        if (line == null) return;
        if (line.isEmpty()) {
            dispatch();
            return;
        }
        if (line.startsWith(":")) return; // Kommentar / Keep-Alive
        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : line.substring(colon + 1);
        if (value.startsWith(" ")) value = value.substring(1);
        switch (field) {
            case "event" -> eventName = value;
            case "data" -> {
                if (!data.isEmpty()) data.append('\n');
                data.append(value);
            }
            case "id" -> {
                if (!value.contains("\0")) eventId = value;
            }
            default -> { /* retry und unbekannte Felder ignorieren */ }
        }
    }

    private void dispatch() {
        if (eventId != null) {
            lastEventId = eventId;
        }
        if (data.isEmpty() && eventName == null) {
            eventId = null;
            return;
        }
        Event ev = new Event(lastEventId, eventName == null ? "message" : eventName, data.toString());
        data.setLength(0);
        eventName = null;
        eventId = null;
        sink.accept(ev);
    }
}
