package com.miniloan.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

/** Prompting and strict input parsing. Bad input re-prompts instead of crashing. */
public class ConsoleIO {
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

    /** Raw line (may be empty). */
    public String line(String label) {
        System.out.print(label);
        System.out.flush();
        try {
            String s = in.readLine();
            if (s == null) throw new InputClosedException();
            return s;
        } catch (IOException e) {
            throw new InputClosedException();
        }
    }

    /** Optional text: blank returns "". */
    public String optional(String label) { return line(label + ": ").trim(); }

    /** Required text. */
    public String required(String label) {
        while (true) {
            String s = line(label + ": ").trim();
            if (!s.isEmpty()) return s;
            System.out.println("  This value is required.");
        }
    }

    public String text(String label, String def) {
        String s = line(label + (def != null ? " [" + def + "]" : "") + ": ").trim();
        return s.isEmpty() ? def : s;
    }

    private <T> T read(String label, T def, Function<String, T> parser, String hint) {
        while (true) {
            String s = line(label + (def != null ? " [" + def + "]" : "") + ": ").trim();
            if (s.isEmpty()) {
                if (def != null) return def;
                System.out.println("  This value is required.");
                continue;
            }
            try {
                return parser.apply(s);
            } catch (RuntimeException e) {
                System.out.println("  Invalid input - " + hint);
            }
        }
    }

    public long longValue(String label) { return read(label, null, Long::parseLong, "enter a whole number."); }

    public int integer(String label, Integer def) { return read(label, def, Integer::parseInt, "enter a whole number."); }

    public BigDecimal decimal(String label, BigDecimal def) {
        return read(label, def, s -> new BigDecimal(s.replace(",", "")), "enter a number, e.g. 50000 or 1115.12.");
    }

    public LocalDate date(String label, LocalDate def) { return read(label, def, LocalDate::parse, "use yyyy-MM-dd, e.g. 1990-05-15."); }

    public <E extends Enum<E>> E choice(String label, Class<E> type, E def) {
        return read(label, def, s -> Enum.valueOf(type, s.toUpperCase()), "choose one of " + java.util.Arrays.toString(type.getEnumConstants()) + ".");
    }

    public <E extends Enum<E>> E optionalChoice(String label, Class<E> type) {
        while (true) {
            String s = line(label + ": ").trim();
            if (s.isEmpty()) return null;
            try { return Enum.valueOf(type, s.toUpperCase()); }
            catch (IllegalArgumentException e) { System.out.println("  Invalid input - choose one of " + java.util.Arrays.toString(type.getEnumConstants()) + " or leave blank."); }
        }
    }

    public boolean confirm(String label) {
        String s = line(label + " (y/n): ").trim().toLowerCase();
        return s.equals("y") || s.equals("yes");
    }
}
