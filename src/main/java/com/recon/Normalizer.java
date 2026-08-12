package com.recon;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Repairs a raw custodian field into canonical form. Format drift is absorbed
 * here so downstream matching compares economics, not string style.
 */
public class Normalizer {

    public static final DateTimeFormatter[] ENGINE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyyMMdd")
    };
    public static final DateTimeFormatter[] LEGACY_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    public String side(String raw) {
        String s = raw.trim().toUpperCase();
        if (s.startsWith("B")) return "BUY";
        if (s.startsWith("S")) return "SELL";
        return null;
    }

    /** Throws NumberFormatException for values such as "N/A" (malformed rows). */
    public double number(String raw) {
        return Double.parseDouble(raw.trim().replace(",", ""));
    }

    public String date(String raw, DateTimeFormatter[] formats) {
        String s = raw.trim();
        for (DateTimeFormatter f : formats) {
            try { return LocalDate.parse(s, f).format(ENGINE_FORMATS[0]); }
            catch (Exception ignored) { }
        }
        return null;
    }
}
