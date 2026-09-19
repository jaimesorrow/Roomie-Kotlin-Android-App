# Roomie

A native Android shared-housing marketplace. People can offer rooms or post that they are seeking roommates, filter posts by city/type/monthly budget, manage posts, and contact each other privately.

Implemented: email/password authentication and verification, password reset, profiles and community consent, server-authorized listings, paginated search, private messaging and message history, block/report, optional generic FCM notifications, expiry, retryable account deletion, and server-authoritative monetization entitlements. The MVP has no ads, enabled purchases, or rent-payment processing.

Free accounts may keep one active listing. Expiring backend-only `plus` and `property_manager` entitlements raise that limit; clients can read only their own entitlement and cannot grant access. Play Billing remains intentionally disabled until the core staging marketplace passes release verification. See [the monetization plan](docs/MONETIZATION.md).

Release application ID: `com.jaimesorrow.roomie`. Debug ID: `com.jaimesorrow.roomie.debug`. The Kotlin namespace remains `com.example.roomie`. Register the exact application IDs with Firebase and Play Console.

## Local verification

Use JDK 17+, Android platform 36/build-tools 35.0.0, Node 22, and the committed Gradle wrapper.

```bash
npm ci
npm --prefix functions ci
npm test
npm run test:rules
./gradlew testDebugUnitTest lintDebug assembleDebug bundleRelease
```

The Firestore emulator verifies read isolation and write denial; backend integration tests verify ownership, deterministic contact, idempotent messages, blocking, expiry, and deletion. Tests use the demo project only.

CI does not require `app/google-services.json`; the implementation shows a setup screen when configuration is absent. Android compilation remains unverified in the current workspace. Signed builds require real configuration and public privacy/deletion URLs.

See [release steps](docs/RELEASE.md), [verification results](docs/READINESS.md), and [monetization plan](docs/MONETIZATION.md).
