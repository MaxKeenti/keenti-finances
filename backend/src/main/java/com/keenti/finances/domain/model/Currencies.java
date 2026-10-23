package com.keenti.finances.domain.model;

import java.util.List;
import java.util.Locale;

/**
 * The ISO 4217 codes Keenti supports (ADR-0024).
 *
 * <p>Adding a code here is the whole change: the database only checks the
 * three-letter shape, and every amount is stored in its own currency.
 */
public final class Currencies {

    public static final String MXN = "MXN";
    public static final String DEFAULT = MXN;
    public static final List<String> SUPPORTED = List.of(MXN, "EUR", "USD", "GBP");

    private Currencies() {}

    public static boolean isSupported(String code) {
        return code != null && SUPPORTED.contains(code);
    }

    /** Upper-cases and trims a code, or returns {@code null} for a blank one. */
    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
