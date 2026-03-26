---
phase: 2
slug: scraping-and-selection
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-26
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 / AndroidX Test / Robolectric (existing from Phase 1) |
| **Config file** | `app/build.gradle.kts` (testInstrumentationRunner already configured) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.ScrapingRepositoryParserTest" --tests "*.ArtistSelectionUseCaseTest" --tests "*.GenreCacheDaoTest" -x lint` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.ScrapingRepositoryParserTest" --tests "*.ArtistSelectionUseCaseTest" --tests "*.GenreCacheDaoTest" -x lint`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 1 | SCRP-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.GenreCacheDaoTest" -x lint` | W0 | pending |
| 2-02-01 | 02 | 2 | SCRP-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ScrapingRepositoryParserTest" -x lint` | W0 | pending |
| 2-02-02 | 02 | 2 | SCRP-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ScrapingRepositoryParserTest" -x lint` | W0 | pending |
| 2-02-03 | 02 | 2 | SCRP-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ScrapingRepositoryParserTest" -x lint` | W0 | pending |
| 2-03-01 | 03 | 2 | SEL-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ArtistSelectionUseCaseTest" -x lint` | W0 | pending |
| 2-03-02 | 03 | 2 | SEL-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ArtistSelectionUseCaseTest" -x lint` | W0 | pending |
| 2-03-03 | 03 | 2 | SCRP-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ArtistSelectionUseCaseTest" -x lint` | W0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/com/musicali/app/data/local/GenreCacheDaoTest.kt` — stubs for GenreCacheDao insert/query/delete
- [ ] `app/src/test/java/com/musicali/app/data/remote/ScrapingRepositoryParserTest.kt` — stubs for SCRP-01, SCRP-02, SCRP-03
- [ ] `app/src/test/java/com/musicali/app/domain/usecase/ArtistSelectionUseCaseTest.kt` — stubs for SEL-01, SEL-02, SCRP-04

*Existing test infrastructure from Phase 1 covers JUnit/Room setup — no new framework installs needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live EveryNoise pages return >=30 artists | SCRP-01 | Requires live network; pages could change | Run app, observe scraping log output |
| Fallback to cache when network unavailable | SCRP-04 | Requires simulating network failure | Disable WiFi/data, trigger playlist generation |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
