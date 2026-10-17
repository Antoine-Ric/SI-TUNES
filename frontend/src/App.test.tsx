import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

const songs = Array.from({ length: 5 }, (_, index) => ({
  title: `Song ${index + 1}`,
  artist: `Artist ${index + 1}`,
  whyItFits: "Its rap tags match your late-night request.",
  streamingLink: "https://music.youtube.com/search?q=track",
  isWildcard: index === 4,
  brokenRule: index === 4 ? "Excluded artist: Drake" : null,
  wildcardExplanation:
    index === 4 ? "A strong catalog match; wildcards are on." : null,
}));

describe("playlist form", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
  it("renders all required controls and disables a blank request", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Build your playlist" }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText("What are you in the mood for?"),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Duration" })).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: "Allow explicit music" }),
    ).toBeChecked();
    expect(
      screen.getByRole("switch", { name: "Make room for a wildcard" }),
    ).not.toBeChecked();
    expect(screen.getByRole("button", { name: "Find songs" })).toBeDisabled();
  });
  it("submits complete API contract, displays loading, then all five songs and wildcard details", async () => {
    const user = userEvent.setup();
    let resolveRequest!: (value: Response) => void;
    vi.mocked(fetch).mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );
    render(<App />);
    await user.type(
      screen.getByLabelText("What are you in the mood for?"),
      "Late-night rap",
    );
    await user.type(
      screen.getByLabelText(/Excluded artists/),
      "Drake, Drake,  Other Artist ",
    );
    await user.type(screen.getByLabelText(/Excluded genres/), "country");
    await user.click(
      screen.getByRole("switch", { name: "Make room for a wildcard" }),
    );
    await user.click(screen.getByRole("button", { name: "Find songs" }));
    expect(
      screen.getByRole("button", { name: "Finding songs..." }),
    ).toBeDisabled();
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(
      JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string),
    ).toEqual({
      prompt: "Late-night rap",
      targetType: "SONG_COUNT",
      targetValue: 20,
      excludedArtists: ["Drake", "Other Artist"],
      excludedGenres: ["country"],
      allowExplicit: true,
      wildcardsEnabled: true,
    });
    resolveRequest(
      new Response(JSON.stringify({ suggestions: songs }), { status: 200 }),
    );
    await waitFor(() =>
      expect(
        screen.getAllByRole("link", { name: "Find on YouTube Music" }),
      ).toHaveLength(5),
    );
    songs.forEach((song) =>
      expect(
        screen.getByRole("heading", { name: song.title }),
      ).toBeInTheDocument(),
    );
    expect(screen.getByText("Excluded artist: Drake")).toBeInTheDocument();
    expect(screen.getByText("WILDCARD")).toBeInTheDocument();
  });
  it("sends duration target and explicit preference", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ suggestions: songs }), { status: 200 }),
    );
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: /Slow Sunday/ }));
    await user.click(screen.getByRole("radio", { name: "Duration" }));
    await user.clear(screen.getByLabelText("Duration in minutes"));
    await user.type(screen.getByLabelText("Duration in minutes"), "45");
    await user.click(
      screen.getByRole("switch", { name: "Allow explicit music" }),
    );
    await user.click(screen.getByRole("button", { name: "Find songs" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    expect(
      JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string),
    ).toMatchObject({
      targetType: "DURATION",
      targetValue: 45,
      allowExplicit: false,
    });
  });
  it("renders a safe error and preserves the request for retry", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ detail: "MusicBrainz is unavailable." }), {
        status: 502,
      }),
    );
    const user = userEvent.setup();
    render(<App />);
    await user.type(
      screen.getByLabelText("What are you in the mood for?"),
      "Late-night rap",
    );
    await user.click(screen.getByRole("button", { name: "Find songs" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "We couldn’t generate suggestions. Try again.",
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "MusicBrainz is unavailable.",
    );
    expect(screen.getByLabelText("What are you in the mood for?")).toHaveValue(
      "Late-night rap",
    );
    expect(screen.getByRole("button", { name: "Find songs" })).toBeEnabled();
  });
  it("rejects an incomplete response instead of displaying fewer than five", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ suggestions: songs.slice(0, 4) }), {
        status: 200,
      }),
    );
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: /After-hours drive/ }));
    await user.click(screen.getByRole("button", { name: "Find songs" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "incomplete set",
    );
    expect(
      screen.queryAllByRole("link", { name: "Find on YouTube Music" }),
    ).toHaveLength(0);
  });
});
