# Current verification

Prepared locally on 2026-09-18. GitHub upload is pending explicit approval following an automatic approval-review rejection.

## Implemented

- Repository started as a welcome-screen skeleton. It now implements verified email accounts, consented profiles, room/roommate posts, city/kind/budget filters, pagination, private conversations, message history, generic notifications, block/report, and reauthenticated account deletion.
- All writes use App Check-enforced callable functions with trusted identity, ownership/membership validation, transaction rate limits, and server timestamps. Firestore rules deny direct writes and isolate private data.
- Deletion is queued and retryable, removes shared conversation histories, and also handles accounts deleted directly through Firebase Auth. Pending cleanup records have no TTL.
- Android targets API 36; debug and release App Check providers, signing guards, CI, and signed-candidate workflows are prepared.
- A server-authoritative entitlement model is prepared. Free, Plus, and Property Manager listing limits are enforced in Functions; clients can read only their own entitlement and cannot write it. Purchase flows remain disabled until the marketplace passes staging validation.
- Missing Firebase configuration shows a setup-incomplete screen. No production backend is connected or deployed by these changes.

## Verified locally

- Six domain tests, six Firestore security-rule emulator tests, and three backend integration tests passed with the updated dependencies: 15 tests total.
- Integration tests cover trusted ownership, outsider denial, deterministic contact, idempotent messages, both directions of blocking, expired listings, server-only paid listing limits, queued/retried deletion, direct Auth deletion, entitlement cleanup, and old-token recreation prevention.
- Functions module loads successfully. Production functions dependency audit reports zero vulnerabilities.
- Kotlin syntax, XML/JSON/YAML parsing, and whitespace checks passed. Syntax parsing is not Android compilation or lint.

## Outstanding before release

- Android compilation, native unit tests, lint, R8, debug APK, and signed bundle remain unverified. Java-based Gradle/SDK downloads fail with a network-unreachable error in the current workspace. CI must pass after upload.
- Separate staging/production Firebase setup, real Android app configuration, Authentication/App Check setup, backend/rules/index deployment, budgets/alerts, and retention policies.
- Operator identity/support, reviewed public privacy and external account-deletion pages, staffed moderation, and upload signing key.
- Real-device staging tests, notification permission/deep-link/sign-out behavior, accessibility, offline failures, and Play Console submission requirements.

See [release steps](RELEASE.md). This is a locally prepared marketplace implementation with release blockers. No production deployment or Play submission was performed.
