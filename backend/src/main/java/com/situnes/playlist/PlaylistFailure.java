package com.situnes.playlist;

public class PlaylistFailure extends RuntimeException {
    private final String code;
    public PlaylistFailure(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }
    public String code() { return code; }
}
