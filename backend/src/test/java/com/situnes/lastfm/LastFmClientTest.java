package com.situnes.lastfm;

import com.situnes.config.MusicProperties;
import com.situnes.music.MusicCandidate;
import com.situnes.playlist.PlaylistFailure;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LastFmClientTest {
    private MockRestServiceServer server;
    private LastFmClient client;
    private final MusicCandidate song = new MusicCandidate("mbid", "Night & Day", "Artist", Set.of("Artist"), Set.of("soul"), 100, MusicCandidate.ContentRating.UNKNOWN);
    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LastFmClient(builder, properties("test-only-key"));
    }
    private MusicProperties properties(String key) { return new MusicProperties("https://musicbrainz.test", "Test", "https://lastfm.test/2.0/", key, 1, 0); }
    @AfterEach void verify() { server.verify(); }
    @Test void mapsTagsAndCachesWithoutChangingCatalogIdentity() {
        server.expect(request -> assertThat(request.getURI().getRawQuery()).contains("method=track.getTopTags", "track=Night%20%26%20Day"))
            .andRespond(withSuccess("{\"toptags\":{\"tag\":[{\"name\":\"hip-hop\",\"count\":100},{\"name\":\"country\",\"count\":0}]}}", MediaType.APPLICATION_JSON));
        var enriched = client.enrich(song);
        assertThat(enriched.id()).isEqualTo(song.id());
        assertThat(enriched.title()).isEqualTo(song.title());
        assertThat(enriched.tags()).containsExactlyInAnyOrder("soul", "hip-hop");
        assertThat(enriched.contentRating()).isEqualTo(MusicCandidate.ContentRating.UNKNOWN);
        assertThat(client.enrich(song)).isEqualTo(enriched);
    }
    @Test void trackMissIsAllowedButDoesNotInventTags() {
        server.expect(anything()).andRespond(withSuccess("{\"error\":6,\"message\":\"Track not found\"}", MediaType.APPLICATION_JSON));
        assertThat(client.enrich(song).tags()).containsExactly("soul");
    }
    @Test void authenticationErrorsAreNotSilentlySkipped() {
        server.expect(anything()).andRespond(withSuccess("{\"error\":10,\"message\":\"Invalid key\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.enrich(song)).isInstanceOf(PlaylistFailure.class).hasMessageNotContaining("test-only-key");
    }
    @Test void httpFailureIsHandled() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.enrich(song)).isInstanceOf(PlaylistFailure.class);
    }
    @Test void malformedDataIsNotReportedAsSuccessfulEnrichment() {
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.enrich(song)).isInstanceOf(PlaylistFailure.class);
    }
    @Test void missingKeyFailsBeforeSendingRequest() {
        var unconfigured = new LastFmClient(RestClient.builder(), properties(""));
        assertThatThrownBy(unconfigured::verifyConfigured).isInstanceOf(PlaylistFailure.class).hasMessageContaining("LASTFM_API_KEY");
    }
}
