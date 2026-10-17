import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { findSuggestions, parseExclusions } from "./api";
import type { SongSuggestion, TargetType } from "./api";

const inspirations = [
  {
    label: "After-hours drive",
    prompt:
      "Late-night rap for driving home after a win. Confident but not overly aggressive.",
  },
  {
    label: "Slow Sunday",
    prompt: "Chill soul for a slow Sunday morning. Relaxing and warm.",
  },
  {
    label: "Pick up the pace",
    prompt: "Energetic electronic music for an afternoon run.",
  },
];

function Arrow({ diagonal = false }: { diagonal?: boolean }) {
  return (
    <svg
      aria-hidden="true"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
    >
      {diagonal ? (
        <path d="M6 18 18 6M6 6h12v12" />
      ) : (
        <path d="M4 12h16m-6-6 6 6-6 6" />
      )}
    </svg>
  );
}

function Toggle({
  label,
  hint,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  hint: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled: boolean;
}) {
  return (
    <label className="toggle-row">
      <span>
        <span className="toggle-title">{label}</span>
        <span className="field-hint">{hint}</span>
      </span>
      <span className="toggle-control">
        <span className="toggle-value" aria-hidden="true">
          {checked ? "On" : "Off"}
        </span>
        <input
          type="checkbox"
          role="switch"
          aria-label={label}
          checked={checked}
          onChange={(event) => onChange(event.target.checked)}
          disabled={disabled}
        />
        <span className="switch-track" aria-hidden="true">
          <span />
        </span>
      </span>
    </label>
  );
}

export default function App() {
  const [prompt, setPrompt] = useState("");
  const [targetType, setTargetType] = useState<TargetType>("SONG_COUNT");
  const [targetValue, setTargetValue] = useState("20");
  const [excludedArtists, setExcludedArtists] = useState("");
  const [excludedGenres, setExcludedGenres] = useState("");
  const [allowExplicit, setAllowExplicit] = useState(true);
  const [wildcardsEnabled, setWildcardsEnabled] = useState(false);
  const [status, setStatus] = useState<
    "idle" | "loading" | "success" | "error"
  >("idle");
  const [songs, setSongs] = useState<SongSuggestion[]>([]);
  const [error, setError] = useState("");
  const [submittedPrompt, setSubmittedPrompt] = useState("");
  const pending = useRef(false);
  const loading = status === "loading";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    pending.current = true;
    setStatus("loading");
    setError("");
    setSongs([]);
    setSubmittedPrompt(prompt.trim());
    try {
      setSongs(
        await findSuggestions({
          prompt: prompt.trim(),
          targetType,
          targetValue: Number(targetValue),
          excludedArtists: parseExclusions(excludedArtists),
          excludedGenres: parseExclusions(excludedGenres),
          allowExplicit,
          wildcardsEnabled,
        }),
      );
      setStatus("success");
    } catch (failure) {
      setError(
        failure instanceof Error && failure.name !== "TimeoutError"
          ? failure.message
          : "The request took too long. Please try again.",
      );
      setStatus("error");
    } finally {
      pending.current = false;
    }
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="wordmark" href="/" aria-label="SI-TUNES home">
          <span className="logo-disc" aria-hidden="true" />
          SI<span className="wordmark-dash">—</span>TUNES
          <span className="beta">LAB</span>
        </a>
        <span className="header-caption">
          A little direction. A new discovery.
        </span>
      </header>
      <main>
        {import.meta.env.VITE_DEMO_MODE === "true" && (
          <p role="status">
            Demo mode · Fixed sample songs. Try “After-hours drive” to explore
            results. Electronic prompts simulate a provider error.
          </p>
        )}
        <section className="hero" aria-labelledby="hero-title">
          <div>
            <p className="eyebrow">
              <span className="live-dot" /> THE PLAYLIST STARTS WITH YOU
            </p>
            <h1 id="hero-title">
              Your moment.
              <br />
              <span>Your next five.</span>
            </h1>
            <p className="hero-copy">
              Tell us the feeling. Set a few boundaries.
              <br className="desktop-break" /> Find real songs for wherever
              you’re headed.
            </p>
          </div>
          <div className="hero-stamp" aria-hidden="true">
            <span>LESS SCROLLING</span>
            <svg viewBox="0 0 100 100">
              <path d="M20 65V35l30-15v60M50 50l30-15v45" />
            </svg>
            <span>MORE DISCOVERY</span>
          </div>
        </section>
        <div className="workspace">
          <section className="builder-panel" aria-labelledby="builder-title">
            <div className="panel-heading">
              <span className="section-number">01 /</span>
              <h2 id="builder-title">Build your playlist</h2>
            </div>
            <form onSubmit={submit}>
              <fieldset disabled={loading} className="form-fields">
                <div className="field prompt-field">
                  <label htmlFor="prompt">What are you in the mood for?</label>
                  <textarea
                    id="prompt"
                    value={prompt}
                    onChange={(event) => setPrompt(event.target.value)}
                    required
                    maxLength={1000}
                    rows={4}
                    placeholder="Late-night rap for the drive home. Confident, a little reflective, nothing too aggressive…"
                    aria-describedby="prompt-help"
                  />
                  <div className="textarea-footer">
                    <span id="prompt-help">
                      A genre + a feeling is a good place to start.
                    </span>
                    <span>{prompt.length}/1000</span>
                  </div>
                </div>
                <div className="inspiration-row">
                  <span>Try a direction</span>
                  {inspirations.map((item) => (
                    <button
                      type="button"
                      key={item.label}
                      onClick={() => setPrompt(item.prompt)}
                    >
                      {item.label}
                      <Arrow diagonal />
                    </button>
                  ))}
                </div>
                <div className="length-field">
                  <fieldset className="length-choice">
                    <legend>Playlist length</legend>
                    <div className="segmented-control">
                      <label
                        className={targetType === "SONG_COUNT" ? "active" : ""}
                      >
                        <input
                          type="radio"
                          name="targetType"
                          value="SONG_COUNT"
                          checked={targetType === "SONG_COUNT"}
                          onChange={() => setTargetType("SONG_COUNT")}
                        />
                        Songs
                      </label>
                      <label
                        className={targetType === "DURATION" ? "active" : ""}
                      >
                        <input
                          type="radio"
                          name="targetType"
                          value="DURATION"
                          checked={targetType === "DURATION"}
                          onChange={() => setTargetType("DURATION")}
                        />
                        Duration
                      </label>
                    </div>
                  </fieldset>
                  <div className="target-input">
                    <label className="sr-only" htmlFor="target-value">
                      {targetType === "SONG_COUNT"
                        ? "Number of songs"
                        : "Duration in minutes"}
                    </label>
                    <input
                      id="target-value"
                      type="number"
                      min="1"
                      max="1440"
                      step="1"
                      value={targetValue}
                      onChange={(event) => setTargetValue(event.target.value)}
                      required
                    />
                    <span>{targetType === "SONG_COUNT" ? "songs" : "min"}</span>
                  </div>
                </div>
                <p className="field-hint length-note">
                  We’ll start with five suggestions, whatever your target.
                </p>
                <div className="exclusion-grid">
                  <div className="field">
                    <label htmlFor="artists">
                      Excluded artists{" "}
                      <span className="optional">optional</span>
                    </label>
                    <input
                      id="artists"
                      value={excludedArtists}
                      maxLength={2000}
                      onChange={(event) =>
                        setExcludedArtists(event.target.value)
                      }
                      placeholder="e.g. Drake, Taylor Swift"
                    />
                    <span className="field-hint">
                      Separate names with commas.
                    </span>
                  </div>
                  <div className="field">
                    <label htmlFor="genres">
                      Excluded genres <span className="optional">optional</span>
                    </label>
                    <input
                      id="genres"
                      value={excludedGenres}
                      maxLength={2000}
                      onChange={(event) =>
                        setExcludedGenres(event.target.value)
                      }
                      placeholder="e.g. country, metal"
                    />
                    <span className="field-hint">
                      Based on available catalog tags.
                    </span>
                  </div>
                </div>
                <div className="preferences">
                  <Toggle
                    label="Allow explicit music"
                    hint="When off, songs without a clean label are excluded."
                    checked={allowExplicit}
                    onChange={setAllowExplicit}
                    disabled={loading}
                  />
                  <Toggle
                    label="Make room for a wildcard"
                    hint="One surprise may break an artist or genre rule. We’ll label it."
                    checked={wildcardsEnabled}
                    onChange={setWildcardsEnabled}
                    disabled={loading}
                  />
                </div>
              </fieldset>
              <button
                className="submit-button"
                type="submit"
                disabled={loading || !prompt.trim()}
              >
                {loading ? (
                  <>
                    <span className="spinner" aria-hidden="true" />
                    Finding songs...
                  </>
                ) : (
                  <>
                    Find songs
                    <Arrow />
                  </>
                )}
              </button>
            </form>
            <p className="form-footnote">
              Real recordings. No made-up songs. No account needed.
            </p>
          </section>
          <section
            className="results-panel"
            aria-labelledby="results-title"
            aria-busy={loading}
          >
            <div className="panel-heading">
              <span className="section-number">02 /</span>
              <h2 id="results-title">Your next five</h2>
              <span className="result-count">
                {status === "success" ? "05 TRACKS" : "THE FIRST CUT"}
              </span>
            </div>
            <div className="sr-only" role="status">
              {loading
                ? "Finding songs..."
                : status === "success"
                  ? "Five song suggestions are ready."
                  : ""}
            </div>
            {status === "idle" && (
              <div className="empty-state">
                <div className="record-illustration" aria-hidden="true">
                  <div className="record-label">
                    <span>
                      SI
                      <br />
                      TUNES
                    </span>
                    <i />
                  </div>
                  <span className="record-spark">✳</span>
                </div>
                <p className="eyebrow">GOOD MUSIC STARTS SOMEWHERE</p>
                <h3>
                  Give your next playlist
                  <br />a little direction.
                </h3>
                <p>
                  Your five discoveries will land here.
                  <br />
                  Start with a mood, a moment, or a genre.
                </p>
                <div className="empty-divider" />
                <span className="small-note">
                  Sourced from MusicBrainz · Enriched by Last.fm
                </span>
              </div>
            )}
            {loading && (
              <div className="loading-state">
                <p className="results-context">
                  Looking through the catalog for your kind of music.
                </p>
                {[1, 2, 3, 4, 5].map((n) => (
                  <div className="skeleton-card" key={n} aria-hidden="true">
                    <span className="track-number">0{n}</span>
                    <div>
                      <i />
                      <i />
                      <i />
                    </div>
                  </div>
                ))}
              </div>
            )}
            {status === "error" && (
              <div className="error-state" role="alert">
                <span className="error-symbol" aria-hidden="true">
                  ↻
                </span>
                <h3>We couldn’t generate suggestions. Try again.</h3>
                <p>{error}</p>
                <p className="small-note">
                  Your request is still here. Adjust it or press Find songs to
                  retry.
                </p>
              </div>
            )}
            {status === "success" && (
              <>
                <p className="results-context">
                  A first cut for <span>“{submittedPrompt}”</span>
                </p>
                <ol className="song-list">
                  {songs.map((song, index) => (
                    <li
                      className="song-card"
                      key={`${song.artist}-${song.title}`}
                    >
                      <span className="track-number">0{index + 1}</span>
                      <div className="song-content">
                        <div className="song-title-row">
                          <h3>{song.title}</h3>
                          {song.isWildcard && (
                            <span className="wildcard-badge">WILDCARD</span>
                          )}
                        </div>
                        <p className="artist-name">{song.artist}</p>
                        <p className="song-reason">{song.whyItFits}</p>
                        {song.isWildcard && (
                          <div className="wildcard-note">
                            <strong>{song.brokenRule}</strong>
                            <p>{song.wildcardExplanation}</p>
                          </div>
                        )}
                        <a
                          className="song-link"
                          href={song.streamingLink}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          Find on YouTube Music
                          <Arrow diagonal />
                        </a>
                      </div>
                    </li>
                  ))}
                </ol>
                <p className="results-footnote">
                  Catalog tags guide the match. External links open a search for
                  each song.
                </p>
              </>
            )}
          </section>
        </div>
      </main>
      <footer>
        <span>
          SI-TUNES <span className="footer-divider">/</span> A discovery in
          progress.
        </span>
        <span>Made for your next moment.</span>
      </footer>
    </div>
  );
}
