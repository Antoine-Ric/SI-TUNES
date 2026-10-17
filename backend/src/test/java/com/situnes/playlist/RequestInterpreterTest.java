package com.situnes.playlist;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RequestInterpreterTest {
    private final RequestInterpreter interpreter = new RequestInterpreter();
    @Test void interpretsSampleAndRecognizesNegatedAggression() {
        var intent = interpreter.interpret("Late-night rap for driving home after a win. Confident but not overly aggressive.");
        assertThat(intent.genres()).containsExactly("hip hop");
        assertThat(intent.moodTerms()).contains("confident").doesNotContain("aggressive");
        assertThat(intent.query()).isEqualTo("(tag:\"hip hop\")");
    }
    @Test void escapesUserLuceneSyntaxByGeneratingNormalizedTerms() {
        assertThat(interpreter.interpret("artist:secret OR *:* \"\\").query()).doesNotContain("*", "\\", "artist:");
    }
    @Test void moodOnlySearchUsesActualTags() {
        assertThat(interpreter.interpret("Chill music for relaxing").query()).contains("tag:\"chill\"", "tag:\"relaxing\"");
    }
    @Test void insufficientPromptProducesActionableError() {
        assertThatThrownBy(() -> interpreter.interpret("a playlist for me")).isInstanceOf(PlaylistFailure.class).hasMessageContaining("genre");
    }
}
