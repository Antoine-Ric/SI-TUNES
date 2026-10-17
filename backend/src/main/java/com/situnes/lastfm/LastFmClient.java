package com.situnes.lastfm;

import com.github.benmanes.caffeine.cache.*;
import com.situnes.config.MusicProperties;
import com.situnes.music.*;
import com.situnes.playlist.PlaylistFailure;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LastFmClient implements TrackEnricher {
    private final RestClient client;
    private final String apiKey;
    private long lastRequestAt;
    private final Cache<String, Set<String>> cache = Caffeine.newBuilder().maximumSize(1000)
        .expireAfterWrite(Duration.ofHours(1)).build();

    public LastFmClient(RestClient.Builder builder, MusicProperties properties) {
        client = builder.clone().baseUrl(properties.lastfmBaseUrl()).defaultHeader("Accept", "application/json").build();
        apiKey = properties.lastfmApiKey();
    }
    @Override public void verifyConfigured() {
        if (apiKey == null || apiKey.isBlank()) throw new PlaylistFailure("MUSIC_CONFIGURATION_MISSING",
            "Last.fm access is not configured. Add LASTFM_API_KEY to the backend environment.");
    }
    @Override public MusicCandidate enrich(MusicCandidate candidate) {
        verifyConfigured();
        var tags = cache.get(candidate.id(), ignored -> fetch(candidate));
        return candidate.withTags(tags);
    }
    private Set<String> fetch(MusicCandidate candidate) {
        try {
            waitForPermit();
            var body = client.get().uri(uri -> uri.queryParam("method", "track.getTopTags")
                .queryParam("artist", "{artist}").queryParam("track", "{track}")
                .queryParam("api_key", "{key}").queryParam("format", "json").queryParam("autocorrect", 1)
                .build(candidate.artist(), candidate.title(), apiKey)).retrieve().body(LastFmTagResponse.class);
            if (body == null) throw new IllegalStateException("Invalid tag response");
            if (body.error() != null) {
                // Last.fm error 6 is a valid catalog miss, not a transport or authentication failure.
                if (body.error() == 6) return Set.of();
                throw new IllegalStateException("Last.fm API failure");
            }
            if (body.toptags() == null || body.toptags().tag() == null) throw new IllegalStateException("Missing tags");
            var tags = new HashSet<String>();
            body.toptags().tag().stream().filter(tag -> tag.name() != null && (tag.count() == null || tag.count() > 0))
                .limit(20).forEach(tag -> tags.add(tag.name()));
            return Set.copyOf(tags);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PlaylistFailure("ENRICHMENT_UNAVAILABLE", "The tag request was interrupted. Try again.");
        } catch (Exception failure) {
            throw new PlaylistFailure("ENRICHMENT_UNAVAILABLE", "Last.fm is unavailable or its API key needs attention. Try again shortly.");
        }
    }

    private synchronized void waitForPermit() throws InterruptedException {
        // Conservative per-process limit of four enrichment calls per second, across users.
        var remaining = 250 - (System.nanoTime() / 1_000_000 - lastRequestAt);
        if (remaining > 0) Thread.sleep(remaining);
        lastRequestAt = System.nanoTime() / 1_000_000;
    }
}
