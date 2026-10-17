package com.situnes.playlist;

import com.situnes.music.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PlaylistService {
    private final MusicCatalog catalog;
    private final TrackEnricher enricher;
    private final RequestInterpreter interpreter;
    private final RecommendationScorer scorer;
    private final StreamingLinkService links;

    public PlaylistService(MusicCatalog catalog, TrackEnricher enricher, RequestInterpreter interpreter,
                           RecommendationScorer scorer, StreamingLinkService links) {
        this.catalog = catalog;
        this.enricher = enricher;
        this.interpreter = interpreter;
        this.scorer = scorer;
        this.links = links;
    }
    private record Ranked(MusicCandidate candidate, double score, List<String> violations) {}

    public PlaylistSuggestionResponse suggest(PlaylistSuggestionRequest request) {
        enricher.verifyConfigured();
        var intent = interpreter.interpret(request.prompt());
        var unique = new LinkedHashMap<String, MusicCandidate>();
        for (var candidate : catalog.search(intent.query())) {
            var identity = TextNormalizer.normalize(candidate.artist()) + "|" + TextNormalizer.normalize(candidate.title());
            unique.putIfAbsent(identity, candidate);
        }
        var ranked = new ArrayList<Ranked>();
        // Bound outbound work. Catalog returns at most 30; enrich at most 16 distinct tracks.
        for (var candidate : unique.values().stream().limit(16).toList()) {
            // Explicit preference is a safety boundary, including for wildcards. Unknown is excluded.
            if (!request.allowExplicit() && candidate.contentRating() != MusicCandidate.ContentRating.CLEAN) continue;
            var enriched = enricher.enrich(candidate);
            ranked.add(new Ranked(enriched, scorer.score(enriched, intent), violations(enriched, request)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(r -> r.candidate().id()));
        var strict = ranked.stream().filter(r -> r.violations().isEmpty()).limit(5).toList();
        var wildcard = request.wildcardsEnabled() ? ranked.stream().filter(r -> !r.violations().isEmpty()).findFirst() : Optional.<Ranked>empty();
        var selected = new ArrayList<>(strict);
        // Include at most one wildcard, and only when it outranks the last strict match or fills slot 5.
        if (wildcard.isPresent() && selected.size() >= 4 && (selected.size() == 4 || wildcard.get().score() > selected.getLast().score())) {
            if (selected.size() == 5) selected.removeLast();
            selected.add(wildcard.get());
        }
        if (selected.size() != 5) throw new PlaylistFailure("INSUFFICIENT_CANDIDATES",
            "We couldn't find five distinct songs within these rules. Try a broader genre or fewer exclusions. Songs with unknown explicit status are excluded when explicit music is off.");
        var suggestions = selected.stream().map(r -> new SongSuggestion(r.candidate().title(), r.candidate().artist(),
            scorer.explain(r.candidate(), intent), links.linkFor(r.candidate()), !r.violations().isEmpty(),
            r.violations().isEmpty() ? null : String.join("; ", r.violations()),
            r.violations().isEmpty() ? null : "This catalog match ranks highly for your prompt but breaks the listed exclusion. Wildcards are enabled.")).toList();
        return new PlaylistSuggestionResponse(suggestions);
    }

    private List<String> violations(MusicCandidate candidate, PlaylistSuggestionRequest request) {
        var violations = new ArrayList<String>();
        for (var artist : request.excludedArtists()) {
            if (candidate.artistNames().stream().anyMatch(name -> TextNormalizer.normalize(name).equals(TextNormalizer.normalize(artist)))) {
                violations.add("Excluded artist: " + artist.strip());
            }
        }
        for (var genre : request.excludedGenres()) {
            if (candidate.tags().stream().anyMatch(tag -> RequestInterpreter.sameGenre(tag, genre))) {
                violations.add("Excluded genre: " + genre.strip());
            }
        }
        return List.copyOf(violations);
    }
}
