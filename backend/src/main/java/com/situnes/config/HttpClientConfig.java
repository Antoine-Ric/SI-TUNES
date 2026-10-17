package com.situnes.config;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClient.Builder musicRestClientBuilder(MusicProperties properties) {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        // Java does not read HTTPS_PROXY automatically. Preserve platform routing for HTTPS,
        // while keeping loopback mock services direct. Never log the proxy URI or API key.
        var proxy = System.getenv("HTTPS_PROXY");
        if (proxy != null && !proxy.isBlank()) {
            var uri = URI.create(proxy);
            var upstream = ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 80));
            builder.proxy(new ProxySelector() {
                @Override public java.util.List<java.net.Proxy> select(URI target) {
                    if ("localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost())) {
                        return java.util.List.of(java.net.Proxy.NO_PROXY);
                    }
                    return upstream.select(target);
                }
                @Override public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException error) {}
            });
        }
        var factory = new JdkClientHttpRequestFactory(builder.build());
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        return RestClient.builder().requestFactory(factory);
    }
}
