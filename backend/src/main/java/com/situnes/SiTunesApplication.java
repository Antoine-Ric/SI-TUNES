package com.situnes;

import com.situnes.config.MusicProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MusicProperties.class)
public class SiTunesApplication {
    public static void main(String[] args) {
        SpringApplication.run(SiTunesApplication.class, args);
    }
}
