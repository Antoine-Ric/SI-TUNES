package com.situnes.playlist;

import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlaylistControllerTest {
    private PlaylistService service;
    private MockMvc mvc;
    private static final String VALID = """
        {"prompt":"late-night rap","targetType":"SONG_COUNT","targetValue":20,
         "excludedArtists":[],"excludedGenres":[],"allowExplicit":true,"wildcardsEnabled":false}
        """;
    @BeforeEach void setup() {
        service = mock(PlaylistService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PlaylistController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @Test void successfulRequestUsesPublicContract() throws Exception {
        var song = new SongSuggestion("Real Song", "Artist", "Its rap tags fit.", "https://music.youtube.com/search?q=artist", false, null, null);
        when(service.suggest(any())).thenReturn(new PlaylistSuggestionResponse(java.util.Collections.nCopies(5, song)));
        mvc.perform(post("/api/playlists/suggestions").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk()).andExpect(jsonPath("$.suggestions.length()").value(5))
            .andExpect(jsonPath("$.suggestions[0].title").value("Real Song"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"\"prompt\":\"\"", "\"prompt\":\"  \"", "\"targetType\":null", "\"targetType\":\"INVALID\"", "\"targetValue\":0", "\"targetValue\":-1", "\"targetValue\":1441", "\"allowExplicit\":null", "\"wildcardsEnabled\":null"})
    void invalidFieldsReturn400BeforeCallingServices(String field) throws Exception {
        var name = field.substring(0, field.indexOf(':'));
        var invalid = VALID.replaceAll(java.util.regex.Pattern.quote(name) + ":(?:\"[^\"]*\"|true|false|20)", java.util.regex.Matcher.quoteReplacement(field));
        mvc.perform(post("/api/playlists/suggestions").contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
    @Test void overlongPromptAndBlankExclusionAreInvalid() throws Exception {
        var invalid = VALID.replace("late-night rap", "x".repeat(1001));
        mvc.perform(post("/api/playlists/suggestions").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/playlists/suggestions").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"excludedArtists\":[]", "\"excludedArtists\":[\" \"]"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void providerFailureHasSafe502ProblemDetail() throws Exception {
        when(service.suggest(any())).thenThrow(new PlaylistFailure("CATALOG_UNAVAILABLE", "MusicBrainz is unavailable."));
        mvc.perform(post("/api/playlists/suggestions").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }
    @Test void unexpectedExceptionsDoNotLeakCredentials() throws Exception {
        when(service.suggest(any())).thenThrow(new RuntimeException("https://example.test/?api_key=sensitive"));
        mvc.perform(post("/api/playlists/suggestions").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    }
    @Test void missingBooleanIsNotSilentlyDefaulted() throws Exception {
        mvc.perform(post("/api/playlists/suggestions").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"allowExplicit\":true,", "")))
            .andExpect(status().isBadRequest());
    }
}
