package com.situnes.musicbrainz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzSearchResponse(List<Recording> recordings) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recording(String id, String title, Integer score, String disambiguation,
                            @JsonProperty("artist-credit") List<ArtistCredit> artistCredit, List<Tag> tags) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistCredit(String name, String joinphrase, Artist artist) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artist(String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tag(String name, Integer count) {}
}
