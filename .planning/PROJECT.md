# MusicAli

## What This Is

An Android music discovery app that scrapes three EveryNoise genre pages (Indietronica, Nu Disco, Indie Soul), selects 65 unique artists weighted proportionally by genre list size, finds each artist's top song via YouTube Data API v3, and builds or replaces a single YouTube Music playlist called "AliMusings" in the user's account. Artist history is persisted in Room so the same artists are not repeated across runs.

## Core Value

One tap generates a fresh 65-song discovery playlist seeded from curated genre lists — no manual curation required.

## Requirements

### Validated (v1.0)

- ✓ Scrape artist names from three EveryNoise genre pages (Indietronica, Nu Disco, Indie Soul) on demand — v1.0
- ✓ Fall back to last cached scrape if EveryNoise is unreachable — v1.0
- ✓ Select exactly 65 unique artists weighted proportionally by genre list size — v1.0
- ✓ Exclude artists already in history from selection — v1.0
- ✓ Track selected artists in Room DB with timestamp; TTL re-eligibility after 5 runs or 90 days — v1.0
- ✓ Sign in with Google account via OAuth (AppAuth PKCE + Credential Manager) — v1.0
- ✓ Persist OAuth tokens and silently refresh without re-login — v1.0
- ✓ Refresh expired token mid-generation transparently via OkHttp interceptor — v1.0
- ✓ Search YouTube Data API v3 for top song per artist — v1.0
- ✓ Cache artist→videoId in Room to avoid repeat quota spend on warm runs — v1.0
- ✓ Skip artists with no YouTube result and continue generation — v1.0
- ✓ Delete and recreate "AliMusings" playlist each run (not item-level delete) — v1.0
- ✓ Insert all found tracks in a single session — v1.0
- ✓ Real-time progress UI across 4 stages (Scraping, Selecting, Searching, Building) — v1.0
- ✓ Single-tap generation trigger — v1.0
- ✓ Post-generation summary: songs added + artists skipped — v1.0

### Active

*(None — all v1.0 requirements shipped)*

### Out of Scope

- Scheduled/background playlist generation — user explicitly wants on-demand only
- Spotify integration — YouTube Music is the sole music backend
- Multi-song-per-artist — one song per artist keeps the playlist maximally varied
- User-configurable genre splits — weighting is automatic based on list size
- Navigation beyond single screen — app is intentionally single-purpose

## Context

**Shipped v1.0 (2026-03-30):** 1,938 LOC Kotlin. 75 unit tests passing. 5 phases, 14 plans.

**Tech stack:** Kotlin 2.3.20 · AGP 9.1.0 · Compose BOM 2026.03.00 · Room 2.8.4 · Hilt 2.59.2 · AppAuth · Retrofit 3 · OkHttp 4.12 · Jsoup 1.21.1 · KSP (no KAPT)

**Quota:** Each run costs ~6,500 YouTube Data API units (65 searches × 100 units). Warm runs with full cache cost near-zero. Default daily quota is 10,000 units.

**Known gaps:** No offline mode beyond scrape cache. No error recovery UI beyond retry button. Artist selection is random-weighted — no user control over genre balance.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| YouTube Music only (no Spotify) | Avoids dual API complexity; playlist lives where user listens | ✓ Good — no issues |
| 65 artists per run (not 150) | 150 × 100 quota units = 15k/run, exceeds 10k daily free tier | ✓ Good — fits comfortably within quota |
| Weighted artist split by list size | Proportional representation feels fairer than equal split | ✓ Good — validated in Phase 2 |
| Replace playlist vs. create new | Keeps library clean; user always knows where to find it | ✓ Good — works cleanly |
| Track artist history with TTL | Maximizes discovery over time — same artists don't repeat | ✓ Good — Room TTL working |
| On-demand generation | Simpler UX; user controls when freshness happens | ✓ Good — UX validated in UAT |
| KSP over KAPT | KAPT broken with Kotlin 2.x K2 compiler | ✓ Good — zero annotation issues |
| cache-first video ID lookup | Warm runs cost near-zero quota; null sentinel caches no-result artists | ✓ Good — Phase 5 verified |
| Delete + recreate playlist semantics | Simpler than item-level diffing; always produces a clean playlist | ✓ Good |

## Constraints

- **Platform**: Android (Kotlin/Jetpack Compose) — no cross-platform requirement
- **Music backend**: YouTube Music + YouTube Data API v3 — no Spotify
- **Auth**: Google OAuth (user account) — playlist created in user's own YT Music library
- **Data source**: EveryNoise HTML scraping — no paid data feeds
- **Scale**: 65 artists, 1 song each → 65-track playlist per run

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-03-30 after v1.0 milestone — all 19 requirements shipped, 75 tests passing, 5 phases complete.*
