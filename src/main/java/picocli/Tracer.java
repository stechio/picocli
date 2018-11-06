package picocli;

import java.io.PrintStream;

import org.apache.commons.lang3.StringUtils;

public class Tracer {
    enum TraceLevel {
        OFF, WARN, INFO, DEBUG;
        public boolean isEnabled(TraceLevel other) {
            return ordinal() >= other.ordinal();
        }

        void print(Tracer tracer, String msg, Object... params) {
            if (tracer.level.isEnabled(this)) {
                tracer.stream.printf(prefix(msg), params);
            }
        }

        private String prefix(String msg) {
            return "[picocli " + this + "] " + msg;
        }

        static TraceLevel lookup(String key) {
            return key == null ? WARN
                    : StringUtils.isBlank(key) || "true".equalsIgnoreCase(key) ? INFO
                            : valueOf(key);
        }
    }
    
    TraceLevel level = TraceLevel.lookup(System.getProperty("picocli.trace"));
    PrintStream stream = System.err;

    public void warn(String msg, Object... params) {
        TraceLevel.WARN.print(this, msg, params);
    }

    public void info(String msg, Object... params) {
        TraceLevel.INFO.print(this, msg, params);
    }

    public void debug(String msg, Object... params) {
        TraceLevel.DEBUG.print(this, msg, params);
    }

    public boolean isWarn() {
        return level.isEnabled(TraceLevel.WARN);
    }

    public boolean isInfo() {
        return level.isEnabled(TraceLevel.INFO);
    }

    public boolean isDebug() {
        return level.isEnabled(TraceLevel.DEBUG);
    }
}