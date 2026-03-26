# Requirements: MusicAli

**Defined:** 2026-03-25
**Core Value:** One tap generates a fresh 65-song discovery playlist seeded from curated genre lists — no manual curation required.

## v1 Requirements

### Authentication

- [x] **AUTH-01**: User can sign in with their Google account via OAuth (AppAuth PKCE + Credential Manager)
- [x] **AUTH-02**: App persists OAuth tokens (DataStore) and silently refreshes them without requiring re-login
- [x] **AUTH-03**: App recovers gracefully if token expires mid-generation (retry with refreshed token)

### Scraping

- [ ] **SCRP-01**: App scrapes artist names from Indietronica EveryNoise page on demand
- [ ] **SCRP-02**: App scrapes artist names from Nu Disco EveryNoise page on demand
- [ ] **SCRP-03**: App scrapes artist names from Indie Soul EveryNoise page on demand
- [x] **SCRP-04**: App falls back to last cached scrape results if EveryNoise is unreachable

### Artist Selection

- [x] **SEL-01**: App selects exactly 65 unique artists weighted proportionally by genre list size
- [x] **SEL-02**: App excludes artists already in the history database from selection
- [x] **SEL-03**: App tracks each selected artist in Room DB with a timestamp
- [x] **SEL-04**: Artists re-enter the eligible pool after a configurable TTL (default: 5 runs / 90 days)

### YouTube Integration

- [x] **YT-01**: App searches YouTube Data API v3 for a top song for each selected artist
- [ ] **YT-02**: App caches artist → videoId mappings in Room DB to avoid repeat quota spend
- [ ] **YT-03**: App skips artists where no YouTube result is found and continues generation
- [x] **YT-04**: App creates or replaces the "AliMusings" YouTube Music playlist (delete + recreate, not item-level delete)
- [x] **YT-05**: App adds all found tracks to the AliMusings playlist in a single session

### Generation UX

- [x] **UX-01**: User sees a real-time progress indicator during generation (scraping → selecting → searching → building playlist)
- [ ] **UX-02**: User can trigger playlist generation with a single tap
- [x] **UX-03**: App displays count of artists found, songs added, and any skipped artists after generation completes

## v2 Requirements

### Extended UX

- **UXV2-01**: User can view which artists were selected in the last run
- **UXV2-02**: User can manually reset artist history (start fresh)
- **UXV2-03**: User can configure per-genre artist count instead of weighted automatic split

### Resilience

- **RESV2-01**: Exponential backoff for YouTube API rate limit errors (429)
- **RESV2-02**: Partial playlist recovery — resume from last successful track if generation is interrupted

## Out of Scope

| Feature | Reason |
|---------|--------|
| Spotify integration | YouTube Music is the sole backend; dual API doubles complexity |
| Multiple songs per artist | One song keeps the 65-artist playlist maximally varied |
| Scheduled/background generation | On-demand only; WorkManager adds complexity not needed here |
| Creating new playlists per run | Same AliMusings playlist replaced each time to keep library clean |
| Social sharing or export | Single-user personal tool |
| Manual artist curation | Auto-selection from genre lists is the core value |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SEL-03 | Phase 1 | Complete |
| SEL-04 | Phase 1 | Complete |
| SCRP-01 | Phase 2 | Pending |
| SCRP-02 | Phase 2 | Pending |
| SCRP-03 | Phase 2 | Pending |
| SCRP-04 | Phase 2 | Complete |
| SEL-01 | Phase 2 | Complete |
| SEL-02 | Phase 2 | Complete |
| AUTH-01 | Phase 3 | Complete |
| AUTH-02 | Phase 3 | Complete |
| AUTH-03 | Phase 3 | Complete |
| YT-01 | Phase 3 | Complete |
| YT-04 | Phase 3 | Complete |
| YT-05 | Phase 3 | Complete |
| UX-01 | Phase 4 | Complete |
| UX-02 | Phase 4 | Pending |
| UX-03 | Phase 4 | Complete |
| YT-02 | Phase 5 | Pending |
| YT-03 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0

---
*Requirements defined: 2026-03-25*
*Last updated: 2026-03-25 — SEL-01 updated to 65 artists per D-01, SEL-04 updated to 5 runs / 90 days per D-03*
