package com.situnes.music;

import java.util.List;

public interface MusicCatalog {
    List<MusicCandidate> search(String query);
}
