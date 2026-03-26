# Phase 3: Auth and YouTube Integration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-26
**Phase:** 03-auth-and-youtube-integration
**Areas discussed:** Token storage, YouTube search quality, Sign-in trigger, API key storage, YouTube test strategy

---

## Token Storage

| Option | Description | Selected |
|--------|-------------|----------|
| EncryptedSharedPreferences | Android Keystore-backed encryption, purpose-built for secrets | ✓ |
| DataStore Preferences | Already in build but no default encryption — tokens stored plaintext | |
| EncryptedDataStore (Tink) | DataStore + Tink library encryption, coroutine-native but adds complexity | |

**User's choice:** EncryptedSharedPreferences
**Notes:** AUTH-02 requirement says "DataStore" — that is a documentation artifact. ESP is the canonical implementation. DataStore remains in use for the run counter only.

---

## YouTube Search Quality

| Option | Description | Selected |
|--------|-------------|----------|
| Topic channel filter | Search "{artistName} Topic" to find auto-generated channel, get top video | ✓ |
| Artist name only | Plain search.list — noisy, live sets and covers rank alongside studio recordings | |
| Artist name + "official audio" | Works for mainstream acts, misses indie/nu-disco artists frequently | |

**User's choice:** Topic channel filter
**Notes:** Researcher must spike the exact API call sequence — `channelType=topic` is not a first-class parameter in search.list. Fallback to plain search if no Topic channel found.

---

## Sign-in Trigger

| Option | Description | Selected |
|--------|-------------|----------|
| On first launch (eager) | Sign-in screen shown before Generate UI; app always assumes authenticated | ✓ |
| When Generate is tapped (on-demand) | Auth triggered inline; more complex state machine | |
| You decide | Claude picks simpler implementation | |

**User's choice:** On first launch (eager)
**Notes:** Phase 4 UI does not need to handle "not signed in" state during generation — simplifies the Phase 4 state machine.

---

## API Key Storage

| Option | Description | Selected |
|--------|-------------|----------|
| local.properties + BuildConfig | Gitignored, CI-injectable via env var — industry standard | ✓ |
| Hardcoded constant | Simple but leaks into git history | |
| gradle.properties (project-level) | Often committed — safe only in private repos | |

**User's choice:** local.properties + BuildConfig
**Notes:** `YOUTUBE_API_KEY` in local.properties, read in build.gradle.kts, exposed as `BuildConfig.YOUTUBE_API_KEY`. Planner must include setup task instructions.

---

## YouTube Test Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Hand-written fakes | FakeYouTubeRepository implementing interface — consistent with Phase 1/2 pattern | ✓ |
| OkHttp MockWebServer | Full Retrofit+JSON path tested, but slower and needs MockWebServer dep | |
| Integration tests only | No unit tests for YouTube layer — manual only | |

**User's choice:** Hand-written fakes
**Notes:** Auth flow cannot be unit-tested (requires OS-level Credential Manager). Auth is manual/integration-only. YouTube use case logic tested via FakeYouTubeRepository.

---

## Claude's Discretion

- Exact Hilt module structure (new YouTubeModule vs extending NetworkModule)
- Retrofit converter choice (Moshi vs kotlinx.serialization)
- EncryptedSharedPreferences file name and key constants
- AppAuth library version
- Token refresh grace window duration

## Deferred Ideas

None — discussion stayed within phase scope.
