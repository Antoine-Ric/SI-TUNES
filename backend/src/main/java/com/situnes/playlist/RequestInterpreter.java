package com.situnes.playlist;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RequestInterpreter {
    private static final Map<String, List<String>> GENRES;
    static {
        var genres = new LinkedHashMap<String, List<String>>();
        genres.put("hip hop", List.of("hip hop", "hip-hop", "rap"));
        genres.put("r&b", List.of("r&b", "rnb", "rhythm and blues"));
        genres.put("electronic", List.of("electronic", "edm", "techno", "house"));
        genres.put("rock", List.of("rock", "indie rock", "alternative rock"));
        genres.put("pop", List.of("pop", "synthpop", "synth pop"));
        genres.put("jazz", List.of("jazz"));
        genres.put("soul", List.of("soul", "neo soul"));
        genres.put("ambient", List.of("ambient", "instrumental"));
        genres.put("folk", List.of("folk", "acoustic"));
        genres.put("metal", List.of("metal", "heavy metal"));
        genres.put("country", List.of("country"));
        genres.put("classical", List.of("classical"));
        genres.put("reggae", List.of("reggae"));
        genres.put("dance", List.of("dance", "disco"));
        GENRES = Collections.unmodifiableMap(genres);
    }
    public record Intent(String query, Set<String> genres, Set<String> moodTerms, String prompt) {}

    public Intent interpret(String prompt) {
        var genres = new LinkedHashSet<String>();
        GENRES.forEach((genre, aliases) -> {
            if (aliases.stream().anyMatch(alias -> positiveMention(prompt, alias))) genres.add(genre);
        });
        var moods = new LinkedHashSet<String>();
        for (var mood : List.of("chill", "relaxing", "energetic", "happy", "sad", "melancholic", "confident", "aggressive", "reflective")) {
            if (positiveMention(prompt, mood)) moods.add(mood);
        }
        // Mood-only prompts use actual catalog tags. Do not invent artist/song seeds.
        var terms = !genres.isEmpty() ? genres : moods;
        var query = terms.isEmpty() ? fallbackQuery(prompt)
            : terms.stream().map(term -> "tag:\"" + term + "\"").collect(java.util.stream.Collectors.joining(" OR ", "(", ")"));
        return new Intent(query, Set.copyOf(genres), Set.copyOf(moods), prompt.strip());
    }
    private boolean positiveMention(String prompt, String term) {
        var normalized = TextNormalizer.normalize(prompt);
        return TextNormalizer.containsPhrase(prompt, term)
            && !TextNormalizer.containsPhrase(normalized, "not " + term)
            && !TextNormalizer.containsPhrase(normalized, "no " + term)
            && !TextNormalizer.containsPhrase(normalized, "without " + term)
            && !TextNormalizer.containsPhrase(normalized, "not overly " + term);
    }
    private String fallbackQuery(String prompt) {
        var tokens = TextNormalizer.normalize(prompt).split(" ");
        var ignored = Set.of("a", "an", "the", "for", "to", "with", "and", "of", "i", "want", "music", "songs", "playlist", "some", "me");
        var terms = Arrays.stream(tokens).filter(t -> t.length() > 2 && !ignored.contains(t)).limit(4).toList();
        if (terms.isEmpty()) throw new PlaylistFailure("NO_SEARCH_TERMS", "Add a genre, mood, or artist to your request.");
        // Lucene syntax is generated only from normalized tokens, never raw user text.
        return terms.stream().map(term -> "tag:\"" + term + "\"").collect(java.util.stream.Collectors.joining(" OR ", "(", ")"));
    }
    public static boolean sameGenre(String tag, String genre) {
        var normalizedGenre = TextNormalizer.normalize(genre);
        for (var entry : GENRES.entrySet()) {
            if (entry.getValue().stream().anyMatch(alias -> TextNormalizer.normalize(alias).equals(normalizedGenre))) {
                return entry.getValue().stream().anyMatch(alias -> TextNormalizer.containsPhrase(tag, alias));
            }
        }
        return TextNormalizer.containsPhrase(tag, genre);
    }
}
