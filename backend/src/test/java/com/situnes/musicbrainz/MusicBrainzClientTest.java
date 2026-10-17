package com.situnes.musicbrainz;

import com.situnes.config.MusicProperties;
import com.situnes.music.MusicCandidate;
import com.situnes.playlist.PlaylistFailure;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MusicBrainzClientTest {
    private MockRestServiceServer server;
    private MusicBrainzClient client;
    private static final String FIXTURE = """
        {"count":1,"recordings":[{"id":"mbid-1","title":"Night Song","score":100,"disambiguation":"clean version",
        "artist-credit":[{"name":"Lead","joinphrase":" feat. ","artist":{"name":"Lead Artist"}},{"name":"Guest","artist":{"name":"Guest Artist"}}],
        "tags":[{"name":"hip-hop","count":4},{"name":"country","count":-1}]}]}
        """;
    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MusicBrainzClient(builder, new MusicProperties("https://musicbrainz.test/ws/2", "SI-TUNES/Test (https://example.test)", "https://lastfm.test", "test-only", 1, 0));
    }
    @AfterEach void verify() { server.verify(); }
    @Test void mapsCreditsTagsAndCleanMetadataAndCachesSearch() {
        server.expect(request -> assertThat(request.getURI().getRawQuery()).contains("fmt=json", "limit=30", "query="))
            .andExpect(header("User-Agent", "SI-TUNES/Test (https://example.test)"))
            .andRespond(withSuccess(FIXTURE, MediaType.APPLICATION_JSON));
        var results = client.search("tag:\"hip hop\"");
        assertThat(results).hasSize(1);
        var song = results.getFirst();
        assertThat(song.artist()).isEqualTo("Lead feat. Guest");
        assertThat(song.artistNames()).contains("Lead Artist", "Guest Artist");
        assertThat(song.tags()).containsExactly("hip-hop");
        assertThat(song.contentRating()).isEqualTo(MusicCandidate.ContentRating.CLEAN);
        assertThat(client.search("tag:\"hip hop\"")).isEqualTo(results);
    }
    @Test void radioEditIsNotAssumedClean() {
        server.expect(anything()).andRespond(withSuccess(FIXTURE.replace("clean version", "radio edit"), MediaType.APPLICATION_JSON));
        assertThat(client.search("rap").getFirst().contentRating()).isEqualTo(MusicCandidate.ContentRating.UNKNOWN);
    }
    @Test void dropsIncompleteRecordings() {
        server.expect(anything()).andRespond(withSuccess("{\"recordings\":[{\"id\":\"one\",\"title\":\"No artist\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.search("rap")).isEmpty();
    }
    @Test void downstreamErrorsAreSafe() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.search("rap")).isInstanceOf(PlaylistFailure.class).hasMessage("MusicBrainz is unavailable. Try again shortly.");
    }
    @Test void malformedDataIsAProviderFailure() {
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.search("rap")).isInstanceOf(PlaylistFailure.class);
    }
}
