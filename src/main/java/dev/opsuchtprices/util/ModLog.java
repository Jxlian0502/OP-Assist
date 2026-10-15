package dev.opsuchtprices.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Zentrales Logging. Debug-Ausgaben nur, wenn in der Config aktiviert. */
public final class ModLog {
    private ModLog() {}

    public static final Logger LOG = LoggerFactory.getLogger("opsuchtprices");
    private static volatile boolean debug = false;

    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void debug(String msg, Object... args) {
        if (debug) {
            LOG.info("[debug] " + msg, args);
        }
    }

    public static void info(String msg, Object... args) {
        LOG.info(msg, args);
    }

    public static void warn(String msg, Object... args) {
        LOG.warn(msg, args);
    }
}
