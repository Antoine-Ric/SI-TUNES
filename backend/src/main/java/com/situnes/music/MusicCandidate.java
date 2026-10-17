package com.situnes.music;

import java.util.Set;

public record MusicCandidate(String id, String title, String artist, Set<String> artistNames,
                             Set<String> tags, int catalogScore, ContentRating contentRating) {
    public enum ContentRating { CLEAN, EXPLICIT, UNKNOWN }
    public MusicCandidate withTags(Set<String> enrichedTags) {
        var combined = new java.util.HashSet<>(tags);
        combined.addAll(enrichedTags);
        return new MusicCandidate(id, title, artist, artistNames, Set.copyOf(combined), catalogScore, contentRating);
    }
}
