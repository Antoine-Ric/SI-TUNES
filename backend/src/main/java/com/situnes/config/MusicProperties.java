package com.situnes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("music")
public record MusicProperties(String musicbrainzBaseUrl, String musicbrainzUserAgent,
                              String lastfmBaseUrl, String lastfmApiKey,
                              int timeoutSeconds, long musicbrainzIntervalMs) {}
