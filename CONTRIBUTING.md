# Contributing

Use GitHub Flow. Keep `main` stable. Start a short-lived branch from current `main` (`feat/...`, `fix/...`, or `chore/...`), make focused conventional commits, and open a pull request. Run the checks in README before requesting review. Squash merge approved work and remove its branch. Do not push implementation directly to `main`.

The cloud task already provides an isolated checkout. Work in it; do not create a Git worktree unless explicitly requested.

Recommended GitHub settings: protect `main`, require pull requests and the `validate` CI check, prohibit force pushes/deletion, enable squash merging, and auto-delete merged branches. These settings require repository administration and are not assumed to be configured.

Keep external API DTOs in their provider packages, and keep catalog, enrichment, scoring, and external-link implementations behind interfaces. Do not introduce AI-generated catalog entries. Credentials belong in environment settings or an ignored local environment file, never in source, tests, URLs in logs, or browser bundles.

Ticket 1 has no persistence. Accounts, saved playlists, feedback, exports, streaming playback, concerts, embeddings, and production deployment need separate tickets.
