package com.situnes.playlist;

import com.situnes.music.MusicCandidate;

public interface RecommendationScorer {
    double score(MusicCandidate candidate, RequestInterpreter.Intent intent);
    String explain(MusicCandidate candidate, RequestInterpreter.Intent intent);
}
