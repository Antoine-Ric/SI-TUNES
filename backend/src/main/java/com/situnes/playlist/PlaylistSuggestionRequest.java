package com.situnes.playlist;

import jakarta.validation.constraints.*;
import java.util.List;

public record PlaylistSuggestionRequest(
    @NotBlank @Size(max = 1000) String prompt,
    @NotNull TargetType targetType,
    @NotNull @Min(1) @Max(1440) Integer targetValue,
    @Size(max = 20) List<@NotBlank @Size(max = 100) String> excludedArtists,
    @Size(max = 20) List<@NotBlank @Size(max = 100) String> excludedGenres,
    @NotNull Boolean allowExplicit,
    @NotNull Boolean wildcardsEnabled
) {
    public PlaylistSuggestionRequest {
        excludedArtists = excludedArtists == null ? List.of() : List.copyOf(excludedArtists);
        excludedGenres = excludedGenres == null ? List.of() : List.copyOf(excludedGenres);
    }
}
