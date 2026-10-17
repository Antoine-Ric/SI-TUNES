package com.situnes.playlist;

import com.situnes.music.MusicCandidate;
import org.springframework.stereotype.Component;

@Component
public class TagRecommendationScorer implements RecommendationScorer {
    @Override public double score(MusicCandidate candidate, RequestInterpreter.Intent intent) {
        long genreMatches = intent.genres().stream().filter(genre -> candidate.tags().stream().anyMatch(tag -> RequestInterpreter.sameGenre(tag, genre))).count();
        long moodMatches = intent.moodTerms().stream().filter(mood -> candidate.tags().stream().anyMatch(tag -> TextNormalizer.containsPhrase(tag, mood))).count();
        double artistMatch = TextNormalizer.containsPhrase(intent.prompt(), candidate.artist()) ? 15 : 0;
        return candidate.catalogScore() / 10.0 + genreMatches * 20 + moodMatches * 8 + artistMatch;
    }
    @Override public String explain(MusicCandidate candidate, RequestInterpreter.Intent intent) {
        var matchingTags = candidate.tags().stream().filter(tag ->
            intent.genres().stream().anyMatch(genre -> RequestInterpreter.sameGenre(tag, genre)) ||
            intent.moodTerms().stream().anyMatch(mood -> TextNormalizer.containsPhrase(tag, mood)))
            .sorted().limit(2).toList();
        var excerpt = intent.prompt().replaceAll("\\s+", " ");
        if (excerpt.length() > 110) excerpt = excerpt.substring(0, 107) + "…";
        if (!matchingTags.isEmpty()) {
            return "Its " + String.join(" / ", matchingTags) + " tags connect to your request: “" + excerpt + "”.";
        }
        return "MusicBrainz matched this recording to your request “" + excerpt + "”; its mood fit is not verified.";
    }
}
