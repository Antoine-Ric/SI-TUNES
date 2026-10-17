package com.situnes.playlist;

public record SongSuggestion(String title, String artist, String whyItFits, String streamingLink,
                             boolean isWildcard, String brokenRule, String wildcardExplanation) {}
