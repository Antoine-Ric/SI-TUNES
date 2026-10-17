# SI-TUNES

Describe a playlist, set boundaries, and discover the first **five real songs**. Ticket 1 implements a React → Spring Boot → MusicBrainz → Last.fm vertical slice. It does not generate fictional songs or require an LLM key.

## Run locally

On macOS or Linux, start from the repository root with Python 3.11+, curl, and tar installed:

```sh
bash scripts/setup-local.sh
bash scripts/dev.sh --demo
```

Open **http://127.0.0.1:5173**, choose **After-hours drive**, and click **Find songs**. Demo mode uses a fixed set of test songs through the real backend; no API key is required. Electronic prompts deliberately simulate a provider failure, and disabling explicit music demonstrates insufficient clean data. A visible banner identifies demo mode. Demo fixtures use port 9012, separate from browser tests.

Setup downloads checksum-verified **JDK 21** and **Node.js 24 LTS** when needed into ignored `.local/tools/`, installs frozen dependencies, verifies/builds the backend, builds the frontend, and installs Playwright Chromium. Caches stay under ignored `.cache/`. It does not change system Java, Node, or shell configuration. On Linux, Playwright may additionally require system browser libraries (`npx playwright install --with-deps chromium`).

For live discovery, edit the ignored `.env` created by setup and set `LASTFM_API_KEY` using your key from [Last.fm API accounts](https://www.last.fm/api/account/create), then restart with:

```sh
bash scripts/dev.sh
```

Live mode loads `.env` as trusted shell configuration. Quote values containing spaces. The key is used by the backend; never put it in a `VITE_*` variable. Without a key, the UI and health endpoint work, but suggestions return a configuration error. Demo mode is only enabled by the explicit `--demo` flag; live mode never falls back to fixtures.

For manual tool commands in Bash or Zsh, run `source scripts/local-env.sh` first to select the project runtimes. Maven 3.9.16 is bootstrapped by the checksum-pinned wrapper. Frontend versions are pinned in `package-lock.json`.

If you already have JDK 21 and a supported Node version, you can also install manually:

1. Install and build:

   ```sh
   cd backend
   ./mvnw --batch-mode --no-transfer-progress verify
   cd ../frontend
   npm ci
   npm run build
   cd ..
   ```

2. Set `LASTFM_API_KEY` in the ignored root `.env` or the backend process environment. `.env.example` documents the variable. Never commit or put this key in a `VITE_*` variable.

3. From the repository root, run:

   ```sh
   bash scripts/dev.sh
   ```

   The development UI listens on port **5173**, and the API on **8080**, both bound to loopback. Vite proxies `/api` to the backend, so no permissive CORS policy is needed. Ctrl+C stops the processes started by the script. The API health endpoint is `/actuator/health`.

For backend hot development, use `cd backend && ./mvnw spring-boot:run` and run `npm run dev` in `frontend/` in a separate terminal. `scripts/dev.sh` runs the last built JAR; rebuild after changing Java code.

## Codex cloud environment

Use the existing `/workspace/SI-TUNES` checkout. Cloud tasks are already isolated; do not create worktrees unless requested. Keep implementation on a feature branch.

```sh
cd /workspace/SI-TUNES
bash scripts/setup-cloud.sh
source scripts/cloud-env.sh
bash scripts/dev.sh
```

The cloud setup script installs an official checksum-verified JDK 21 if needed, retains the platform's proxy/TLS trust, configures Maven for the provided proxy, installs frozen npm dependencies, and runs backend, frontend, and browser checks. It stores generated settings/caches outside the checkout under `/workspace`. The image's system Chromium is used for browser tests; live processes need restarting in future tasks.

Required outbound hosts are `repo.maven.apache.org`, `registry.npmjs.org`, `download.oracle.com` (JDK bootstrap), `musicbrainz.org`, and `ws.audioscrobbler.com`. MusicBrainz and Last.fm access must be enabled in environment settings. Supply `LASTFM_API_KEY` there securely. A saved draft does not apply network changes or publish the environment.

## API

`POST /api/playlists/suggestions`

```json
{
  "prompt": "Late-night rap for driving home after a win. Confident but not overly aggressive.",
  "targetType": "SONG_COUNT",
  "targetValue": 20,
  "excludedArtists": ["Drake"],
  "excludedGenres": ["country"],
  "allowExplicit": true,
  "wildcardsEnabled": true
}
```

`targetType` accepts `SONG_COUNT` or `DURATION` (minutes). `targetValue` is a positive integer up to 1440. Ticket 1 always returns the first five, regardless of the collected target. Prompts must contain 1–1000 characters. Exclusion lists accept at most 20 entries of up to 100 characters each. Both booleans are required.

Successful responses contain exactly five `suggestions`, each with `title`, `artist`, `whyItFits`, `streamingLink`, `isWildcard`, `brokenRule`, and `wildcardExplanation`. Normal songs have null wildcard details. Links open a YouTube Music search; they are not verified streaming IDs or a playback integration.

Errors use RFC 9457 Problem Details. Invalid inputs return **400**; provider/configuration/insufficient-candidate failures return **502** with a safe `detail` and machine-readable `code`; unexpected failures return **500** without internal details or credentials. The app never fills gaps with invented or fixture songs.

## How recommendations work

An initial deterministic interpreter recognizes common English genres and mood terms. It generates bounded MusicBrainz tag searches, maps real recording/artist credits, deduplicates title/artist pairs, and enriches up to 16 candidates with Last.fm track tags. A replaceable scorer ranks catalog relevance plus genre, mood, and artist matches. Explanations refer to observed tags and the user's prompt; they do not claim verified musical/audio characteristics.

Artist exclusions match credited/collaborating artists without case or accent sensitivity. Genre exclusions use observed catalog/community tags and common aliases, including rap/hip hop. With wildcards off, known exclusion violations are always rejected. With wildcards on, at most one artist/genre exception is allowed and both its broken rule and explanation are returned. At least four strict matches are required.

**Explicit-data limitation:** MusicBrainz and Last.fm do not supply a dependable universal explicit-content flag. Only recordings labelled `clean` in MusicBrainz disambiguation are treated as clean; radio edits are not assumed clean, and Last.fm community tags cannot certify lyrics. Explicit and unknown recordings are excluded when `allowExplicit=false`, even with wildcards enabled. This may produce insufficient matches. A trusted content-rating provider is needed before promising certified clean playlists.

MusicBrainz requests include an identifying User-Agent, are serialized at intervals of at least 1.1 seconds, and have a bounded 15-minute cache. Last.fm is limited to four calls/second per process with a bounded one-hour cache. Network timeouts are 8 seconds. Rate limits are process-local; distributed deployment is outside this ticket. Last.fm error 6 (track not found) permits un-enriched catalog data; auth, transport, and malformed-response errors remain visible. Missing genre tags and broad prompts can reduce recommendation quality; no semantic AI, embeddings, or mood inference from audio is implemented.

## Checks

After local setup, run all checks (backend, lint, unit tests, frontend build, and browser tests) with:

```sh
bash scripts/check-local.sh
```

Or run checks individually:

```sh
source scripts/local-env.sh
cd backend && ./mvnw --batch-mode --no-transfer-progress verify
cd ../frontend
npm ci
npm run lint
npm run test
npm run build
npx playwright install --with-deps chromium
npm run test:e2e
```

In the cloud, source `scripts/cloud-env.sh` first and pass `-s /workspace/.cache/si-tunes/maven-settings.xml -Dmaven.repo.local=/workspace/.cache/m2` to Maven, or run `scripts/setup-cloud.sh` for all checks.

- Backend tests cover service selection, filtering, wildcard boundaries, validation, controllers, credential-safe errors, and both external response mappings.
- Frontend tests cover form controls, request payloads, loading, five-song rendering, retries/errors, response validation, and unsafe external links.
- Playwright starts the **actual Spring Boot server**, Vite, and isolated Python HTTP fixtures for both music providers. It exercises desktop/mobile results, strict explicit policy, upstream failures/retry, API validation, and automated axe WCAG A/AA checks. These tests need no real API key and **do not prove live provider access**. Fixtures run on port 9011; test API on 8081; test UI on 4174. There is no fixture fallback in live startup.

CI runs these checks for pull requests and feature/fix/chore branches. See [CONTRIBUTING.md](CONTRIBUTING.md) for the branching strategy and recommended protections.

## Layout

```text
backend/       Java 21 + Spring Boot API; provider clients, service interfaces, tests
frontend/      React + TypeScript + Vite; accessible form/results, Vitest, Playwright
scripts/       Local/cloud startup, readiness check, isolated HTTP test fixtures
.github/       Pull-request template and continuous integration
docs/          Build status and known acceptance limitations
```

PostgreSQL is deferred because Ticket 1 does not store data. Accounts, persistence, feedback, export, playback, concert features, richer AI, and production deployment require later tickets.
