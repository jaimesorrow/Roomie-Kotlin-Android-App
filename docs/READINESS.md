# Current verification

Published to GitHub as pull request #3. Verification below reflects the green `Production verification` workflow at commit `466c1313f5c16aed87a829043d20dda34ba9ecab` on 2026-09-19.

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
- GitHub Actions passed Android native unit tests, lint, debug APK assembly, and release AAB bundling on a clean Ubuntu runner with JDK 17, Android platform 36, and build-tools 35.0.0.
- The workflow produced an `android-verification` artifact containing reports and build outputs. This CI artifact is not a production-signed release.

## Outstanding before release

- Production signing and the credential-gated signed-release workflow remain unverified because the upload key, real Firebase configuration, and public policy/support values have not been supplied.
- Separate staging/production Firebase setup, real Android app configuration, Authentication/App Check setup, backend/rules/index deployment, budgets/alerts, and retention policies.
- Operator identity/support, reviewed public privacy and external account-deletion pages, staffed moderation, and upload signing key.
- Real-device staging tests, notification permission/deep-link/sign-out behavior, accessibility, offline failures, and Play Console submission requirements.

See [release steps](RELEASE.md). This is a compiled and backend-tested marketplace implementation with external release blockers. No production Firebase deployment, signed production build, or Play submission was performed.
