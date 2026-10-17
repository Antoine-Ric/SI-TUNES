package com.situnes.playlist;

import com.situnes.music.MusicCandidate;

public interface StreamingLinkService {
    String linkFor(MusicCandidate candidate);
}
