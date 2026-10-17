package com.situnes.music;

public interface TrackEnricher {
    void verifyConfigured();
    MusicCandidate enrich(MusicCandidate candidate);
}
