package com.situnes.playlist;

import com.situnes.music.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaylistServiceTest {
    private final MusicCatalog catalog = mock(MusicCatalog.class);
    private final TrackEnricher enricher = mock(TrackEnricher.class);
    private final PlaylistService service = new PlaylistService(catalog, enricher, new RequestInterpreter(),
        new TagRecommendationScorer(), new YouTubeMusicLinkService());

    @BeforeEach void enrichment() {
        when(enricher.enrich(any())).thenAnswer(call -> call.getArgument(0));
    }
    private MusicCandidate track(int id, String artist, Set<String> tags, MusicCandidate.ContentRating rating) {
        return new MusicCandidate("id-" + id, "Song " + id, artist, Set.of(artist), tags, 100 - id, rating);
    }
    private List<MusicCandidate> five() {
        return java.util.stream.IntStream.range(1, 6).mapToObj(i -> track(i, "Artist " + i, Set.of("hip-hop"), MusicCandidate.ContentRating.CLEAN)).toList();
    }
    private PlaylistSuggestionRequest request(List<String> artists, List<String> genres, boolean explicit, boolean wildcard) {
        return new PlaylistSuggestionRequest("Late-night rap for driving home after a win. Confident but not overly aggressive.",
            TargetType.SONG_COUNT, 20, artists, genres, explicit, wildcard);
    }
    @Test void returnsFiveCatalogSongsWithExplanationsAndEncodedLinks() {
        when(catalog.search(anyString())).thenReturn(five());
        var songs = service.suggest(request(List.of(), List.of(), true, false)).suggestions();
        assertThat(songs).hasSize(5).allSatisfy(song -> {
            assertThat(song.whyItFits()).contains("hip-hop", "Late-night rap");
            assertThat(song.streamingLink()).startsWith("https://music.youtube.com/search?q=Artist+");
            assertThat(song.isWildcard()).isFalse();
            assertThat(song.brokenRule()).isNull();
        });
        verify(enricher, times(5)).enrich(any());
    }
    @Test void artistAndGenreExclusionsAreCaseInsensitiveAndNormalizeAliases() {
        var candidates = new ArrayList<>(five());
        candidates.add(track(6, "Dráké", Set.of("rap"), MusicCandidate.ContentRating.CLEAN));
        candidates.add(track(7, "Country Artist", Set.of("alt-country"), MusicCandidate.ContentRating.CLEAN));
        when(catalog.search(anyString())).thenReturn(candidates);
        assertThat(service.suggest(request(List.of("DRAKE"), List.of("country"), true, false)).suggestions())
            .hasSize(5).noneMatch(song -> song.artist().equals("Dráké") || song.artist().equals("Country Artist"));
    }
    @Test void excludesCollaboratingArtists() {
        var candidates = new ArrayList<>(five());
        candidates.add(new MusicCandidate("collab", "Collaboration", "Guest feat. Drake", Set.of("Guest", "Drake"), Set.of("rap"), 100, MusicCandidate.ContentRating.CLEAN));
        when(catalog.search(anyString())).thenReturn(candidates);
        assertThat(service.suggest(request(List.of("Drake"), List.of(), true, false)).suggestions()).noneMatch(s -> s.title().equals("Collaboration"));
    }
    @Test void excludesExactArtistIdentityRatherThanUnrelatedNames() {
        var candidates = new ArrayList<>(five());
        candidates.add(track(0, "Drake Bell", Set.of("rap"), MusicCandidate.ContentRating.CLEAN));
        candidates.add(track(6, "Drake", Set.of("rap"), MusicCandidate.ContentRating.CLEAN));
        when(catalog.search(anyString())).thenReturn(candidates);
        assertThat(service.suggest(request(List.of("Drake"), List.of(), true, false)).suggestions())
            .anyMatch(s -> s.artist().equals("Drake Bell")).noneMatch(s -> s.artist().equals("Drake"));
    }
    @Test void lastfmTagsParticipateInGenreExclusions() {
        var candidates = new ArrayList<>(five());
        var genreMatch = track(7, "Hidden country artist", Set.of(), MusicCandidate.ContentRating.CLEAN);
        candidates.add(genreMatch);
        when(catalog.search(anyString())).thenReturn(candidates);
        when(enricher.enrich(genreMatch)).thenReturn(genreMatch.withTags(Set.of("country")));
        assertThat(service.suggest(request(List.of(), List.of("country"), true, false)).suggestions()).noneMatch(s -> s.artist().equals(genreMatch.artist()));
    }
    @Test void hipHopExclusionAlsoMatchesRap() {
        when(catalog.search(anyString())).thenReturn(five());
        assertThatThrownBy(() -> service.suggest(request(List.of(), List.of("rap"), true, false))).isInstanceOf(PlaylistFailure.class);
    }
    @Test void explicitOffRejectsUnknownAndExplicitEvenWithWildcards() {
        var candidates = new ArrayList<>(five());
        candidates.add(track(6, "Unknown Artist", Set.of("rap"), MusicCandidate.ContentRating.UNKNOWN));
        candidates.add(track(7, "Explicit Artist", Set.of("rap"), MusicCandidate.ContentRating.EXPLICIT));
        when(catalog.search(anyString())).thenReturn(candidates);
        assertThat(service.suggest(request(List.of(), List.of(), false, true)).suggestions())
            .hasSize(5).noneMatch(s -> s.artist().equals("Unknown Artist") || s.artist().equals("Explicit Artist"));
        verify(enricher, times(5)).enrich(any());
    }
    @Test void noSyntheticFillersWhenFewerThanFiveMatch() {
        when(catalog.search(anyString())).thenReturn(five().subList(0, 4));
        assertThatThrownBy(() -> service.suggest(request(List.of(), List.of(), true, false)))
            .isInstanceOf(PlaylistFailure.class).hasMessageContaining("five distinct songs");
    }
    @Test void deduplicatesDifferentCatalogIdsForSameArtistAndTitle() {
        var candidates = new ArrayList<>(five());
        var original = five().getFirst();
        candidates.addFirst(new MusicCandidate("duplicate", original.title().toUpperCase(), original.artist().toUpperCase(), original.artistNames(), original.tags(), 100, original.contentRating()));
        when(catalog.search(anyString())).thenReturn(candidates);
        assertThat(service.suggest(request(List.of(), List.of(), true, false)).suggestions())
            .hasSize(5).extracting(s -> TextNormalizer.normalize(s.artist()) + TextNormalizer.normalize(s.title())).doesNotHaveDuplicates();
    }
    @Test void atMostOneWildcardAndItIsFullyLabeled() {
        var candidates = new ArrayList<>(five().subList(0, 4));
        candidates.add(track(6, "Drake", Set.of("rap"), MusicCandidate.ContentRating.CLEAN));
        candidates.add(track(7, "Other banned", Set.of("rap"), MusicCandidate.ContentRating.CLEAN));
        when(catalog.search(anyString())).thenReturn(candidates);
        var songs = service.suggest(request(List.of("Drake", "Other banned"), List.of(), true, true)).suggestions();
        assertThat(songs).hasSize(5);
        assertThat(songs.stream().filter(SongSuggestion::isWildcard).toList()).hasSize(1).allSatisfy(s -> {
            assertThat(s.brokenRule()).contains("Excluded artist");
            assertThat(s.wildcardExplanation()).isNotBlank();
        });
    }
    @Test void wildcardsCannotReplaceMoreThanOneStrictMatch() {
        when(catalog.search(anyString())).thenReturn(five());
        assertThatThrownBy(() -> service.suggest(request(List.of("Artist 1", "Artist 2"), List.of(), true, true))).isInstanceOf(PlaylistFailure.class);
    }
    @Test void durationTargetStillReturnsFirstFive() {
        when(catalog.search(anyString())).thenReturn(five());
        assertThat(service.suggest(new PlaylistSuggestionRequest("rap", TargetType.DURATION, 45, List.of(), List.of(), true, false)).suggestions()).hasSize(5);
    }
    @Test void downstreamFailureIsNotReplacedWithFakeSongs() {
        when(catalog.search(anyString())).thenThrow(new PlaylistFailure("CATALOG_UNAVAILABLE", "Catalog unavailable"));
        assertThatThrownBy(() -> service.suggest(request(List.of(), List.of(), true, false))).isInstanceOf(PlaylistFailure.class);
    }
}
