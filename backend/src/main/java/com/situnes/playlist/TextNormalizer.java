package com.situnes.playlist;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {}
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    public static boolean containsPhrase(String text, String phrase) {
        return (" " + normalize(text) + " ").contains(" " + normalize(phrase) + " ");
    }
}
