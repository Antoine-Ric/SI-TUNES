import { afterEach, describe, expect, it, vi } from "vitest";
import { findSuggestions, parseExclusions } from "./api";
import type { PlaylistRequest } from "./api";

const request: PlaylistRequest = {
  prompt: "rap",
  targetType: "SONG_COUNT",
  targetValue: 20,
  excludedArtists: [],
  excludedGenres: [],
  allowExplicit: true,
  wildcardsEnabled: false,
};
const song = {
  title: "Song",
  artist: "Artist",
  whyItFits: "rap",
  streamingLink: "https://music.youtube.com/search",
  isWildcard: false,
  brokenRule: null,
  wildcardExplanation: null,
};

describe("API boundary", () => {
  afterEach(() => vi.unstubAllGlobals());
  it("trims and deduplicates comma-separated exclusions", () => {
    expect(parseExclusions("Drake, , Drake, Prince ")).toEqual([
      "Drake",
      "Prince",
    ]);
  });
  it("handles non-JSON upstream errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("<html>Bad gateway</html>", { status: 502 }),
        ),
    );
    await expect(findSuggestions(request)).rejects.toThrow(
      "music service is unavailable",
    );
  });
  it("rejects unsafe external links", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({
              suggestions: Array(5).fill({
                ...song,
                streamingLink: "javascript:alert(1)",
              }),
            }),
          ),
        ),
    );
    await expect(findSuggestions(request)).rejects.toThrow("incomplete");
  });
  it("rejects unlabeled wildcard responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({
              suggestions: Array(5).fill({ ...song, isWildcard: true }),
            }),
          ),
        ),
    );
    await expect(findSuggestions(request)).rejects.toThrow("incomplete");
  });
});
