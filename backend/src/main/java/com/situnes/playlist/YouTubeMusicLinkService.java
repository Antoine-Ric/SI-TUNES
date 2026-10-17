package com.situnes.playlist;

import com.situnes.music.MusicCandidate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class YouTubeMusicLinkService implements StreamingLinkService {
    @Override public String linkFor(MusicCandidate candidate) {
        return "https://music.youtube.com/search?q=" + URLEncoder.encode(candidate.artist() + " " + candidate.title(), StandardCharsets.UTF_8);
    }
}
