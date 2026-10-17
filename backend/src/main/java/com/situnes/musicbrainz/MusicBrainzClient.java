package com.situnes.musicbrainz;

import com.github.benmanes.caffeine.cache.*;
import com.situnes.config.MusicProperties;
import com.situnes.music.*;
import com.situnes.playlist.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MusicBrainzClient implements MusicCatalog {
    private final RestClient client;
    private final long intervalMs;
    private long lastRequestAt;
    private final Cache<String, List<MusicCandidate>> cache = Caffeine.newBuilder().maximumSize(100)
        .expireAfterWrite(Duration.ofMinutes(15)).build();

    public MusicBrainzClient(RestClient.Builder builder, MusicProperties properties) {
        client = builder.clone().baseUrl(properties.musicbrainzBaseUrl())
            .defaultHeader("User-Agent", properties.musicbrainzUserAgent()).defaultHeader("Accept", "application/json").build();
        intervalMs = properties.musicbrainzIntervalMs();
    }
    @Override public List<MusicCandidate> search(String query) {
        return cache.get(query, this::fetch);
    }
    private List<MusicCandidate> fetch(String query) {
        try {
            MusicBrainzSearchResponse body;
            // Serialize outbound catalog requests to respect MusicBrainz's 1 request/second rule.
            synchronized (this) {
                var remaining = intervalMs - (System.nanoTime() / 1_000_000 - lastRequestAt);
                if (remaining > 0) Thread.sleep(remaining);
                lastRequestAt = System.nanoTime() / 1_000_000;
                body = client.get().uri(uri -> uri.path("/recording").queryParam("query", "{query}")
                    .queryParam("fmt", "json").queryParam("limit", 30).build(query)).retrieve().body(MusicBrainzSearchResponse.class);
            }
            if (body == null || body.recordings() == null) throw new IllegalStateException("Invalid catalog response");
            return body.recordings().stream().map(this::map).flatMap(Optional::stream).toList();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PlaylistFailure("CATALOG_UNAVAILABLE", "The music catalog request was interrupted. Try again.");
        } catch (Exception failure) {
            throw new PlaylistFailure("CATALOG_UNAVAILABLE", "MusicBrainz is unavailable. Try again shortly.");
        }
    }
    private Optional<MusicCandidate> map(MusicBrainzSearchResponse.Recording recording) {
        if (recording.id() == null || recording.title() == null || recording.title().isBlank()
            || recording.artistCredit() == null || recording.artistCredit().isEmpty()) return Optional.empty();
        var display = new StringBuilder();
        var names = new HashSet<String>();
        for (var credit : recording.artistCredit()) {
            var name = credit.name() != null ? credit.name() : credit.artist() == null ? null : credit.artist().name();
            if (name == null || name.isBlank()) continue;
            names.add(name);
            if (credit.artist() != null && credit.artist().name() != null) names.add(credit.artist().name());
            display.append(name).append(credit.joinphrase() == null ? "" : credit.joinphrase());
        }
        if (names.isEmpty()) return Optional.empty();
        var tags = new HashSet<String>();
        if (recording.tags() != null) recording.tags().stream().filter(t -> t.name() != null && (t.count() == null || t.count() > 0))
            .forEach(tag -> tags.add(tag.name()));
        var description = recording.disambiguation() == null ? "" : recording.disambiguation();
        var rating = TextNormalizer.containsPhrase(description, "explicit") ? MusicCandidate.ContentRating.EXPLICIT
            : TextNormalizer.containsPhrase(description, "clean")
            ? MusicCandidate.ContentRating.CLEAN : MusicCandidate.ContentRating.UNKNOWN;
        return Optional.of(new MusicCandidate(recording.id(), recording.title(), display.toString().strip(), Set.copyOf(names), Set.copyOf(tags),
            recording.score() == null ? 0 : recording.score(), rating));
    }
}
