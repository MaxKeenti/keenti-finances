package com.keenti.finances.infrastructure.adapter.in.rest;

import java.util.Set;

/**
 * Single backend source of truth for the per-User font choices. Referenced by
 * {@link UserPreferencesRequest}'s Bean Validation and by tests. The frontend
 * keeps its own copy of these labels because they're also UI strings (dropdown
 * options + CSS font-family map); keeping the two in sync is a manual step
 * called out in CAPABILITIES "Font preset list".
 *
 * The {@code *_REGEX} constants are built from compile-time string literal
 * concatenation so they can be used by {@code @Pattern}, which requires a
 * compile-time constant expression. If you add a font, update three things in
 * this file: the {@link Set}, the regex, and you're done — but the order they
 * appear in the regex must match the alternation written below.
 */
public final class FontPresets {

    public static final String BODY_GEIST = "Geist";
    public static final String BODY_INTER = "Inter";
    public static final String BODY_SYSTEM = "System UI";

    public static final String HEADING_FRAUNCES = "Fraunces";
    public static final String HEADING_PLAYFAIR = "Playfair Display";

    public static final Set<String> BODY = Set.of(BODY_GEIST, BODY_INTER, BODY_SYSTEM);
    public static final Set<String> HEADING = Set.of(HEADING_FRAUNCES, HEADING_PLAYFAIR);

    /** Compile-time-constant regex matching exactly one of the body fonts. */
    public static final String BODY_REGEX = "^(" + BODY_GEIST + "|" + BODY_INTER + "|" + BODY_SYSTEM + ")$";

    /** Compile-time-constant regex matching exactly one of the heading fonts. */
    public static final String HEADING_REGEX = "^(" + HEADING_FRAUNCES + "|" + HEADING_PLAYFAIR + ")$";

    private FontPresets() {}
}
