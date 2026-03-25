# MusicAli

## What This Is

An Android music discovery app that scrapes three EveryNoise genre pages (Indietronica, Nu Disco, Indie Soul), randomly selects 150 unique artists weighted by list size, finds each artist's top song on YouTube Music, and builds or refreshes a single YouTube Music playlist called "AliMusings" in the user's account.

## Core Value

One tap generates a fresh 150-song discovery playlist seeded from curated genre lists — no manual curation required.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Scrape artist names from the three EveryNoise genre pages on demand
- [ ] Select 150 unique artists weighted proportionally by genre list size
- [ ] Track artist history to avoid repeating artists across playlist generations
- [ ] Search YouTube Music for the top song for each selected artist
- [ ] Sign in with the user's Google account via OAuth
- [ ] Create or replace the "AliMusings" YouTube Music playlist with the 150 found tracks
- [ ] Show generation progress to the user (not a silent background operation)
- [ ] On-demand trigger — user taps a button to generate

### Out of Scope

- Scheduled/background playlist generation — user explicitly wants on-demand only
- Spotify integration — YouTube Music is the sole music backend
- Multi-song-per-artist — one song per artist keeps the playlist maximally varied
- Creating new playlists on each run — the same "AliMusings" playlist is always replaced
- User-configurable genre splits — weighting is automatic based on list size

## Context

- EveryNoise pages are HTML and must be scraped (no official API)
- YouTube Music has no official Android SDK; the YouTube Data API v3 covers search and playlist management, but YT Music-specific features (like unofficial `innertube` API) may be needed for accurate "top song" lookup
- The app targets a single user (the developer), so hardcoded genre list URLs are acceptable
- Artist history must persist across app sessions (local storage / Room DB)

## Constraints

- **Platform**: Android (Kotlin/Jetpack Compose) — no cross-platform requirement
- **Music backend**: YouTube Music + YouTube Data API v3 — no Spotify
- **Auth**: Google OAuth (user account) — playlist created in user's own YT Music library
- **Data source**: EveryNoise HTML scraping — no paid data feeds
- **Scale**: 150 artists, 1 song each → 150-track playlist per run

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| YouTube Music only (no Spotify) | Avoids dual API complexity; playlist lives where user listens | — Pending |
| Weighted artist split by list size | Proportional representation feels fairer than equal split | — Pending |
| Replace playlist vs. create new | Keeps library clean; user always knows where to find it | — Pending |
| Track artist history | Maximizes discovery over time — same artists shouldn't repeat | — Pending |
| On-demand generation | Simpler UX; user controls when freshness happens | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-03-25 after initialization*
