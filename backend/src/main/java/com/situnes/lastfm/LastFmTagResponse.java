package com.situnes.lastfm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LastFmTagResponse(TopTags toptags, Integer error, String message) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopTags(List<Tag> tag) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tag(String name, Integer count) {}
}
