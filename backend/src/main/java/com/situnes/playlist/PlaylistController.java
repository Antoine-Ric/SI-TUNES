package com.situnes.playlist;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {
    private final PlaylistService service;
    public PlaylistController(PlaylistService service) { this.service = service; }

    @PostMapping("/suggestions")
    public PlaylistSuggestionResponse suggest(@Valid @RequestBody PlaylistSuggestionRequest request) {
        return service.suggest(request);
    }
}
