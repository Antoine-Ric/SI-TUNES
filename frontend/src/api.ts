export type TargetType = "SONG_COUNT" | "DURATION";
export interface PlaylistRequest {
  prompt: string;
  targetType: TargetType;
  targetValue: number;
  excludedArtists: string[];
  excludedGenres: string[];
  allowExplicit: boolean;
  wildcardsEnabled: boolean;
}
export interface SongSuggestion {
  title: string;
  artist: string;
  whyItFits: string;
  streamingLink: string;
  isWildcard: boolean;
  brokenRule: string | null;
  wildcardExplanation: string | null;
}

function isSuggestion(value: unknown): value is SongSuggestion {
  if (typeof value !== "object" || value === null) return false;
  const song = value as Record<string, unknown>;
  if (
    !["title", "artist", "whyItFits", "streamingLink"].every(
      (key) => typeof song[key] === "string" && song[key].trim().length > 0,
    )
  )
    return false;
  if (typeof song.isWildcard !== "boolean") return false;
  if (
    song.isWildcard &&
    (typeof song.brokenRule !== "string" ||
      typeof song.wildcardExplanation !== "string")
  )
    return false;
  try {
    return new URL(song.streamingLink as string).protocol === "https:";
  } catch {
    return false;
  }
}

export async function findSuggestions(
  request: PlaylistRequest,
): Promise<SongSuggestion[]> {
  const response = await fetch("/api/playlists/suggestions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
    signal: AbortSignal.timeout(150_000),
  });
  if (!response.ok) {
    let detail = "";
    try {
      const problem = (await response.json()) as { detail?: unknown };
      if (typeof problem.detail === "string") detail = problem.detail;
    } catch {
      /* An upstream proxy can return a non-JSON error. */
    }
    throw new Error(
      detail || "The music service is unavailable. Please try again shortly.",
    );
  }
  const data = (await response.json()) as { suggestions?: unknown };
  if (
    !Array.isArray(data.suggestions) ||
    data.suggestions.length !== 5 ||
    !data.suggestions.every(isSuggestion)
  ) {
    throw new Error(
      "The music service returned an incomplete set of songs. Please try again.",
    );
  }
  return data.suggestions;
}

export function parseExclusions(value: string): string[] {
  return [
    ...new Set(
      value
        .split(",")
        .map((part) => part.trim())
        .filter(Boolean),
    ),
  ];
}
